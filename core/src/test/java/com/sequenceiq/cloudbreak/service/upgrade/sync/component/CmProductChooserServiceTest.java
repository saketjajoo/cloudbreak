package com.sequenceiq.cloudbreak.service.upgrade.sync.component;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.cloud.model.ClouderaManagerProduct;
import com.sequenceiq.cloudbreak.cloud.model.ClouderaManagerRepo;
import com.sequenceiq.cloudbreak.cluster.model.ParcelInfo;
import com.sequenceiq.cloudbreak.cluster.model.ParcelStatus;

@ExtendWith(MockitoExtension.class)
public class CmProductChooserServiceTest {
    private static final String PARCEL_VERSION_1 = "7.2.12";

    private static final String PARCEL_VERSION_2 = "7.2.15";

    private static final String PARCEL_VERSION_3 = "7.2.7";

    private static final String PARCEL_NAME_1 = "ParcelName1";

    private static final String PARCEL_NAME_2 = "ParcelName2";

    private final CmProductChooserService underTest = new CmProductChooserService();

    @Test
    void testChooseParcelProductWhenMatchingNameAndVersionThenReturns() {
        Set<ParcelInfo> activeParcels = Set.of(new ParcelInfo(PARCEL_NAME_1, PARCEL_VERSION_1, ParcelStatus.ACTIVATED));
        Set<ClouderaManagerProduct> candidateProducts = Set.of(new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_1));

        Set<ClouderaManagerProduct> foundProducts = underTest.chooseParcelProduct(activeParcels, candidateProducts);

        assertThat(foundProducts, hasSize(1));
        ClouderaManagerProduct foundProduct = foundProducts.iterator().next();
        assertEquals(PARCEL_NAME_1, foundProduct.getName());
        assertEquals(PARCEL_VERSION_1, foundProduct.getVersion());
    }

    @Test
    void testChooseParcelProductWhenMultipleMatchingNameAndVersionThenReturnsAllMatches() {
        Set<ParcelInfo> activeParcels = Set.of(
                new ParcelInfo(PARCEL_NAME_1, PARCEL_VERSION_1, ParcelStatus.ACTIVATED),
                new ParcelInfo(PARCEL_NAME_2, PARCEL_VERSION_2, ParcelStatus.ACTIVATED)
        );
        Set<ClouderaManagerProduct> candidateProducts = Set.of(
                new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_1),
                new ClouderaManagerProduct().withName(PARCEL_NAME_2).withVersion(PARCEL_VERSION_2)
        );

        Set<ClouderaManagerProduct> foundProducts = underTest.chooseParcelProduct(activeParcels, candidateProducts);

        assertThat(foundProducts, hasSize(2));
        List<ClouderaManagerProduct> foundProductList = new ArrayList<>(foundProducts);
        foundProductList.sort(Comparator.comparing(ClouderaManagerProduct::getName));
        assertEquals(PARCEL_NAME_1, foundProductList.get(0).getName());
        assertEquals(PARCEL_VERSION_1, foundProductList.get(0).getVersion());
        assertEquals(PARCEL_NAME_2, foundProductList.get(1).getName());
        assertEquals(PARCEL_VERSION_2, foundProductList.get(1).getVersion());
    }

    @Test
    void testChooseParcelProductWhenMultipleMatchingNameAndVersionThenReturnsOne() {
        Set<ParcelInfo> activeParcels = Set.of(new ParcelInfo(PARCEL_NAME_1, PARCEL_VERSION_1, ParcelStatus.ACTIVATED));
        Set<ClouderaManagerProduct> candidateProducts = Set.of(
                new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_1),
                new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_1)
        );

        Set<ClouderaManagerProduct> foundProducts = underTest.chooseParcelProduct(activeParcels, candidateProducts);

        assertThat(foundProducts, hasSize(1));
        ClouderaManagerProduct foundProduct = foundProducts.iterator().next();
        assertEquals(PARCEL_NAME_1, foundProduct.getName());
        assertEquals(PARCEL_VERSION_1, foundProduct.getVersion());
    }

    @Test
    void testChooseParcelProductWhenMatchingNameButDifferentVersionThenEmptyResult() {
        Set<ParcelInfo> activeParcels = Set.of(new ParcelInfo(PARCEL_NAME_1, PARCEL_VERSION_1, ParcelStatus.ACTIVATED));
        Set<ClouderaManagerProduct> candidateProducts = Set.of(new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_3));

        Set<ClouderaManagerProduct> foundProducts = underTest.chooseParcelProduct(activeParcels, candidateProducts);

        assertThat(foundProducts, hasSize(0));
    }

    @Test
    void testChooseParcelProductWhenMatchingNameButNewerActiveVersionThenReturnsOne() {
        Set<ParcelInfo> activeParcels = Set.of(new ParcelInfo(PARCEL_NAME_1, PARCEL_VERSION_1, ParcelStatus.ACTIVATED));
        Set<ClouderaManagerProduct> candidateProducts = Set.of(new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_2));

        Set<ClouderaManagerProduct> foundProducts = underTest.chooseParcelProduct(activeParcels, candidateProducts);

        assertThat(foundProducts, hasSize(1));
        assertEquals(PARCEL_VERSION_2, foundProducts.iterator().next().getVersion());
    }

    @Test
    void testChooseParcelProductWhenMatchingNameButEqualActiveVersionThenReturnsOne() {
        Set<ParcelInfo> activeParcels = Set.of(new ParcelInfo(PARCEL_NAME_1, PARCEL_VERSION_1, ParcelStatus.ACTIVATED));
        Set<ClouderaManagerProduct> candidateProducts = Set.of(new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_1));

        Set<ClouderaManagerProduct> foundProducts = underTest.chooseParcelProduct(activeParcels, candidateProducts);

        assertThat(foundProducts, hasSize(1));
        assertEquals(PARCEL_VERSION_1, foundProducts.iterator().next().getVersion());
    }

    @Test
    void testChooseParcelProductWhenDifferentNameButSameVersionThenEmptyResult() {
        Set<ParcelInfo> activeParcels = Set.of(new ParcelInfo(PARCEL_NAME_2, PARCEL_VERSION_1, ParcelStatus.ACTIVATED));
        Set<ClouderaManagerProduct> candidateProducts = Set.of(new ClouderaManagerProduct().withName(PARCEL_NAME_1).withVersion(PARCEL_VERSION_1));

        Set<ClouderaManagerProduct> foundProducts = underTest.chooseParcelProduct(activeParcels, candidateProducts);

        assertThat(foundProducts, hasSize(0));
    }

    @Test
    void testChooseCmRepoWhenRepoWithMatchingVersionPresentThenRepoChosen() {
        Optional<String> installedCmVersion = Optional.of("cmVersion1");
        Set<ClouderaManagerRepo> candidateCmRepos = Set.of(
                new ClouderaManagerRepo().withVersion("cmVersion1"),
                new ClouderaManagerRepo().withVersion("cmVersion2")
        );

        Optional<ClouderaManagerRepo> foundCmRepo = underTest.chooseCmRepo(installedCmVersion, candidateCmRepos);

        assertTrue(foundCmRepo.isPresent());
        assertEquals("cmVersion1", foundCmRepo.get().getVersion());
    }

    @Test
    void testChooseCmRepoWhenRepoWithMatchingVersionMissingThenNoRepoChosen() {
        Optional<String> installedCmVersion = Optional.of("cmVersion9");
        Set<ClouderaManagerRepo> candidateCmRepos = Set.of(
                new ClouderaManagerRepo().withVersion("cmVersion1"),
                new ClouderaManagerRepo().withVersion("cmVersion2")
        );

        Optional<ClouderaManagerRepo> foundCmRepo = underTest.chooseCmRepo(installedCmVersion, candidateCmRepos);

        assertTrue(foundCmRepo.isEmpty());
    }

    @Test
    void testChooseCmRepoWhenInstalledCmVersionNotPresentThenNoRepoChosen() {
        Optional<String> installedCmVersion = Optional.empty();
        Set<ClouderaManagerRepo> candidateCmRepos = Set.of(
                new ClouderaManagerRepo().withVersion("cmVersion1"),
                new ClouderaManagerRepo().withVersion("cmVersion2")
        );

        Optional<ClouderaManagerRepo> foundCmRepo = underTest.chooseCmRepo(installedCmVersion, candidateCmRepos);

        assertTrue(foundCmRepo.isEmpty());
    }

}
