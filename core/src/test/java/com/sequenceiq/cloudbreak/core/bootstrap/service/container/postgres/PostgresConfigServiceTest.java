package com.sequenceiq.cloudbreak.core.bootstrap.service.container.postgres;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sequenceiq.cloudbreak.cloud.model.ClouderaManagerRepo;
import com.sequenceiq.cloudbreak.cluster.service.ClusterComponentConfigProvider;
import com.sequenceiq.cloudbreak.common.type.Versioned;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.dto.DatabaseSslDetails;
import com.sequenceiq.cloudbreak.dto.StackDto;
import com.sequenceiq.cloudbreak.orchestrator.model.SaltPillarProperties;
import com.sequenceiq.cloudbreak.service.cluster.DatabaseSslService;
import com.sequenceiq.cloudbreak.service.rdsconfig.RdsConfigProviderFactory;
import com.sequenceiq.cloudbreak.service.rdsconfig.RedbeamsDbServerConfigurer;
import com.sequenceiq.cloudbreak.service.upgrade.rds.UpgradeRdsBackupRestoreStateParamsProvider;
import com.sequenceiq.cloudbreak.view.ClusterView;
import com.sequenceiq.cloudbreak.view.StackView;

@ExtendWith(MockitoExtension.class)
class PostgresConfigServiceTest {

    private static final String SSL_CERTS_FILE_PATH = "/foo/bar.pem";

    private static final String POSTGRES_COMMON = "postgres-common";

    private static final String POSTGRESQL_SERVER = "postgresql-server";

    private static final String POSTGRES_VERSION = "postgres_version";

    private static final String DBVERSION = "dbversion";

    private static final Long CLUSTER_ID = 123L;

    @Mock
    private RdsConfigProviderFactory rdsConfigProviderFactory;

    @Mock
    private RedbeamsDbServerConfigurer dbServerConfigurer;

    @Mock
    private EmbeddedDatabaseConfigProvider embeddedDatabaseConfigProvider;

    @Mock
    private DatabaseSslService databaseSslService;

    @Mock
    private UpgradeRdsBackupRestoreStateParamsProvider upgradeRdsBackupRestoreStateParamsProvider;

    @Mock
    private ClusterComponentConfigProvider clusterComponentProvider;

    @InjectMocks
    private PostgresConfigService underTest;

    @Mock
    private StackDto stack;

    @BeforeEach
    void setUp() {
        Cluster cluster = new Cluster();
        when(stack.getCluster()).thenReturn(cluster);
    }

    @Test
    void decorateServicePillarWithPostgresIfNeededTestCertsWhenSslDisabledFromDatabaseSslService() {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();
        Cluster cluster = new Cluster();
        cluster.setDbSslEnabled(false);
        cluster.setDbSslRootCertBundle(null);
        when(stack.getCluster()).thenReturn(cluster);
        when(databaseSslService.getDbSslDetailsForCreationAndUpdateInCluster(stack)).thenReturn(new DatabaseSslDetails(new HashSet<>(), false));

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        assertThat(servicePillar).isEmpty();
        verify(upgradeRdsBackupRestoreStateParamsProvider, times(1)).createParamsForRdsBackupRestore(stack, "");
        verify(databaseSslService, never()).isDbSslEnabledByClusterView(any(StackView.class), any(ClusterView.class));
    }

    static Object[][] sslDisabledFromClusterDataProvider() {
        return new Object[][]{
                // dbSslEnabled, dbSslRootCertBundle
                {false, ""},
                {false, " "},
        };
    }

    @ParameterizedTest()
    @MethodSource("sslDisabledFromClusterDataProvider")
    void decorateServicePillarWithPostgresIfNeededTestCertsWhenSslDisabledFromCluster(boolean dbSslEnabled, String dbSslRootCertBundle) {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();
        StackView stackView = new Stack();
        when(stack.getStack()).thenReturn(stackView);
        Cluster cluster = new Cluster();
        cluster.setDbSslEnabled(dbSslEnabled);
        cluster.setDbSslRootCertBundle(dbSslRootCertBundle);
        when(stack.getCluster()).thenReturn(cluster);
        when(databaseSslService.isDbSslEnabledByClusterView(stackView, cluster)).thenReturn(dbSslEnabled);

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        assertThat(servicePillar).isEmpty();
        verify(upgradeRdsBackupRestoreStateParamsProvider, times(1)).createParamsForRdsBackupRestore(stack, "");
        verify(databaseSslService, never()).getDbSslDetailsForCreationAndUpdateInCluster(any(StackDto.class));
    }

    @Test
    void decorateServicePillarWithPostgresWhenReusedDatabaseListWasEmpty() {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();
        ReflectionTestUtils.setField(underTest, "databasesReusedDuringRecovery", List.of());
        Cluster cluster = new Cluster();
        cluster.setDbSslEnabled(false);
        when(stack.getCluster()).thenReturn(cluster);
        when(databaseSslService.getDbSslDetailsForCreationAndUpdateInCluster(stack)).thenReturn(new DatabaseSslDetails(new HashSet<>(), false));

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        assertThat(servicePillar).isEmpty();
        verify(databaseSslService, never()).isDbSslEnabledByClusterView(any(StackView.class), any(ClusterView.class));
    }

    @Test
    void decorateServicePillarWithPostgresWhenReusedDatabaseListIsNotEmpty() {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();
        ReflectionTestUtils.setField(underTest, "databasesReusedDuringRecovery", List.of("HIVE"));
        when(stack.getExternalDatabaseEngineVersion()).thenReturn(DBVERSION);
        Cluster cluster = new Cluster();
        cluster.setDbSslEnabled(false);
        when(stack.getCluster()).thenReturn(cluster);
        when(databaseSslService.getDbSslDetailsForCreationAndUpdateInCluster(stack)).thenReturn(new DatabaseSslDetails(new HashSet<>(), false));

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        assertThat(servicePillar).isNotEmpty();
        SaltPillarProperties saltPillarProperties = servicePillar.get(POSTGRESQL_SERVER);
        assertThat(saltPillarProperties).isNotNull();
        assertThat(saltPillarProperties.getPath()).isEqualTo("/postgresql/postgre.sls");

        Map<String, Object> properties = saltPillarProperties.getProperties();
        assertThat(properties).isNotNull();
        assertThat(properties).hasSize(1);

        Map<String, Object> reusedDatabases = (Map<String, Object>) properties.get("postgres");
        assertThat(reusedDatabases).isNotNull();
        assertThat(reusedDatabases).containsOnly(entry("recovery_reused_databases", List.of("HIVE")), entry(POSTGRES_VERSION, DBVERSION));

        verify(databaseSslService, never()).isDbSslEnabledByClusterView(any(StackView.class), any(ClusterView.class));
    }

    @ParameterizedTest(name = "sslEnabledForStack={0}")
    @ValueSource(booleans = {false, true})
    void decorateServicePillarWithPostgresIfNeededTestCertsWhenSslEnabled(boolean sslEnabledForStack) {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();

        Set<String> rootCerts = new LinkedHashSet<>();
        rootCerts.add("cert1");
        rootCerts.add("cert2");
        Cluster cluster = new Cluster();
        cluster.setDbSslRootCertBundle(null);
        cluster.setDatabaseServerCrn("crn");
        cluster.setId(CLUSTER_ID);
        when(stack.getCluster()).thenReturn(cluster);
        when(stack.getExternalDatabaseEngineVersion()).thenReturn(DBVERSION);
        when(dbServerConfigurer.isRemoteDatabaseRequested(any())).thenReturn(true);
        when(databaseSslService.getSslCertsFilePath()).thenReturn(SSL_CERTS_FILE_PATH);
        when(databaseSslService.getDbSslDetailsForCreationAndUpdateInCluster(stack)).thenReturn(new DatabaseSslDetails(rootCerts, sslEnabledForStack));
        when(clusterComponentProvider.getClouderaManagerRepoDetails(CLUSTER_ID)).thenReturn(null);

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        SaltPillarProperties saltPillarProperties = servicePillar.get(POSTGRES_COMMON);
        assertThat(saltPillarProperties).isNotNull();
        assertThat(saltPillarProperties.getPath()).isEqualTo("/postgresql/root-certs.sls");

        Map<String, Object> properties = saltPillarProperties.getProperties();
        assertThat(properties).isNotNull();
        assertThat(properties).hasSize(1);

        Map<String, Object> rootSslCertsMap = (Map<String, Object>) properties.get("postgres_root_certs");
        assertThat(rootSslCertsMap).isNotNull();
        assertThat(rootSslCertsMap).containsOnly(
                entry("ssl_certs", "cert1\ncert2"),
                entry("ssl_certs_file_path", SSL_CERTS_FILE_PATH),
                entry("ssl_restart_required", "false"),
                entry("ssl_for_cm_db_natively_supported", "false"),
                entry("ssl_enabled", String.valueOf(sslEnabledForStack)));

        verify(databaseSslService, never()).isDbSslEnabledByClusterView(any(StackView.class), any(ClusterView.class));
    }

    @ParameterizedTest(name = "sslEnabledForStack={0}")
    @ValueSource(booleans = {false, true})
    void decorateServicePillarWithPostgresIfNeededTestCertsWhenSslEnabledAndRestartRequired(boolean sslEnabledForStack) {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();

        Stack stackView = new Stack();
        stackView.setExternalDatabaseEngineVersion(DBVERSION);
        Cluster cluster = new Cluster();
        cluster.setDbSslEnabled(sslEnabledForStack);
        cluster.setDbSslRootCertBundle("cert1");
        cluster.setDatabaseServerCrn("crn");
        cluster.setId(CLUSTER_ID);
        when(stack.getStack()).thenReturn(stackView);
        when(stack.getCluster()).thenReturn(cluster);
        when(databaseSslService.getSslCertsFilePath()).thenReturn(SSL_CERTS_FILE_PATH);
        when(dbServerConfigurer.isRemoteDatabaseRequested(any())).thenReturn(true);
        when(databaseSslService.isDbSslEnabledByClusterView(stackView, cluster)).thenReturn(sslEnabledForStack);
        when(clusterComponentProvider.getClouderaManagerRepoDetails(CLUSTER_ID)).thenReturn(null);

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        SaltPillarProperties saltPillarProperties = servicePillar.get(POSTGRES_COMMON);
        assertThat(saltPillarProperties).isNotNull();
        assertThat(saltPillarProperties.getPath()).isEqualTo("/postgresql/root-certs.sls");

        Map<String, Object> properties = saltPillarProperties.getProperties();
        assertThat(properties).isNotNull();
        assertThat(properties).hasSize(1);

        Map<String, Object> rootSslCertsMap = (Map<String, Object>) properties.get("postgres_root_certs");
        assertThat(rootSslCertsMap).isNotNull();
        assertThat(rootSslCertsMap).containsOnly(
                entry("ssl_certs", "cert1"),
                entry("ssl_certs_file_path", SSL_CERTS_FILE_PATH),
                entry("ssl_restart_required", "true"),
                entry("ssl_for_cm_db_natively_supported", "false"),
                entry("ssl_enabled", String.valueOf(sslEnabledForStack)));

        verify(databaseSslService, never()).getDbSslDetailsForCreationAndUpdateInCluster(any(StackDto.class));
    }

    static Object[][] sslForCmDbNativeSupportDataProvider() {
        return new Object[][]{
                // cmRepoDetailsAvailable, cmVersion, sslForCmDbNativelySupportedExpected
                {false, null, false},
                {true, null, false},
                {true, "", false},
                {true, " ", false},
                {true, "7.6.2", false},
                {true, "7.9.0", false},
                {true, "7.9.1", false},
                {true, "7.9.2", true},
                {true, "7.9.3", true},
                {true, "7.10.0", true},
        };
    }

    @ParameterizedTest(name = "cmRepoDetailsAvailable={0}, cmVersion={1}")
    @MethodSource("sslForCmDbNativeSupportDataProvider")
    void decorateServicePillarWithPostgresIfNeededTestCertsWhenSslEnabledAndCmDbNativeSupport(boolean cmRepoDetailsAvailable, String cmVersion,
            boolean sslForCmDbNativelySupportedExpected) {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();

        Set<String> rootCerts = new LinkedHashSet<>();
        rootCerts.add("cert1");
        rootCerts.add("cert2");
        Cluster cluster = new Cluster();
        cluster.setDbSslRootCertBundle(null);
        cluster.setDatabaseServerCrn("crn");
        cluster.setId(CLUSTER_ID);
        when(stack.getExternalDatabaseEngineVersion()).thenReturn(DBVERSION);
        when(stack.getCluster()).thenReturn(cluster);
        when(dbServerConfigurer.isRemoteDatabaseRequested(any())).thenReturn(true);
        when(databaseSslService.getSslCertsFilePath()).thenReturn(SSL_CERTS_FILE_PATH);
        when(databaseSslService.getDbSslDetailsForCreationAndUpdateInCluster(stack)).thenReturn(new DatabaseSslDetails(rootCerts, true));
        when(clusterComponentProvider.getClouderaManagerRepoDetails(CLUSTER_ID)).thenReturn(cmRepoDetailsAvailable ? generateCmRepo(() -> cmVersion) : null);

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        SaltPillarProperties saltPillarProperties = servicePillar.get(POSTGRES_COMMON);
        assertThat(saltPillarProperties).isNotNull();
        assertThat(saltPillarProperties.getPath()).isEqualTo("/postgresql/root-certs.sls");

        Map<String, Object> properties = saltPillarProperties.getProperties();
        assertThat(properties).isNotNull();
        assertThat(properties).hasSize(1);

        Map<String, Object> rootSslCertsMap = (Map<String, Object>) properties.get("postgres_root_certs");
        assertThat(rootSslCertsMap).isNotNull();
        assertThat(rootSslCertsMap).containsOnly(
                entry("ssl_certs", "cert1\ncert2"),
                entry("ssl_certs_file_path", SSL_CERTS_FILE_PATH),
                entry("ssl_restart_required", "false"),
                entry("ssl_for_cm_db_natively_supported", String.valueOf(sslForCmDbNativelySupportedExpected)),
                entry("ssl_enabled", "true"));

        verify(databaseSslService, never()).isDbSslEnabledByClusterView(any(StackView.class), any(ClusterView.class));
    }

    private ClouderaManagerRepo generateCmRepo(Versioned version) {
        return new ClouderaManagerRepo()
                .withBaseUrl("baseurl")
                .withGpgKeyUrl("gpgurl")
                .withPredefined(true)
                .withVersion(version.getVersion());
    }

    @ParameterizedTest(name = "cmRepoDetailsAvailable={0}, cmVersion={1}")
    @MethodSource("sslForCmDbNativeSupportDataProvider")
    void decorateServicePillarWithPostgresIfNeededTestCertsWhenSslEnabledAndCmDbNativeSupportAndRestartRequired(boolean cmRepoDetailsAvailable, String cmVersion,
            boolean sslForCmDbNativelySupportedExpected) {
        Map<String, SaltPillarProperties> servicePillar = new HashMap<>();

        Stack stackView = new Stack();
        stackView.setExternalDatabaseEngineVersion(DBVERSION);
        Cluster cluster = new Cluster();
        cluster.setDbSslEnabled(true);
        cluster.setDbSslRootCertBundle("cert1");
        cluster.setDatabaseServerCrn("crn");
        cluster.setId(CLUSTER_ID);
        when(stack.getStack()).thenReturn(stackView);
        when(stack.getCluster()).thenReturn(cluster);
        when(databaseSslService.getSslCertsFilePath()).thenReturn(SSL_CERTS_FILE_PATH);
        when(dbServerConfigurer.isRemoteDatabaseRequested(any())).thenReturn(true);
        when(databaseSslService.isDbSslEnabledByClusterView(stackView, cluster)).thenReturn(true);
        when(clusterComponentProvider.getClouderaManagerRepoDetails(CLUSTER_ID)).thenReturn(cmRepoDetailsAvailable ? generateCmRepo(() -> cmVersion) : null);

        underTest.decorateServicePillarWithPostgresIfNeeded(servicePillar, stack);

        SaltPillarProperties saltPillarProperties = servicePillar.get(POSTGRES_COMMON);
        assertThat(saltPillarProperties).isNotNull();
        assertThat(saltPillarProperties.getPath()).isEqualTo("/postgresql/root-certs.sls");

        Map<String, Object> properties = saltPillarProperties.getProperties();
        assertThat(properties).isNotNull();
        assertThat(properties).hasSize(1);

        Map<String, Object> rootSslCertsMap = (Map<String, Object>) properties.get("postgres_root_certs");
        assertThat(rootSslCertsMap).isNotNull();
        assertThat(rootSslCertsMap).containsOnly(
                entry("ssl_certs", "cert1"),
                entry("ssl_certs_file_path", SSL_CERTS_FILE_PATH),
                entry("ssl_restart_required", "true"),
                entry("ssl_for_cm_db_natively_supported", String.valueOf(sslForCmDbNativelySupportedExpected)),
                entry("ssl_enabled", "true"));

        verify(databaseSslService, never()).getDbSslDetailsForCreationAndUpdateInCluster(any(StackDto.class));
    }

}