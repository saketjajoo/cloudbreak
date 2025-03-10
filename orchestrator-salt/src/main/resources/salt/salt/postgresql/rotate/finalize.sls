{% set configure_remote_db = salt['pillar.get']('postgres:configure_remote_db', 'None') %}

{% if 'None' != configure_remote_db %}

/opt/salt/scripts/rotate/rotate_db_secrets_remote.sh:
  file.managed:
    - makedirs: True
    - user: root
    - group: postgres
    - mode: 750
    - source: salt://postgresql/rotate/scripts/rotate_db_secrets_remote.sh
    - template: jinja

finalize-db-secrets-remote:
  cmd.run:
    - name: runuser -l postgres -s /bin/bash -c '/opt/salt/scripts/rotate/rotate_db_secrets_remote.sh finalize'
    - require:
      - file: /opt/salt/scripts/rotate/rotate_db_secrets_remote.sh

{%- else %}

/opt/salt/scripts/rotate/rotate_db_secrets.sh:
  file.managed:
    - makedirs: True
    - mode: 750
    - user: root
    - group: postgres
    - source: salt://postgresql/rotate/scripts/rotate_db_secrets.sh
    - template: jinja

finalize-db-secrets:
  cmd.run:
    - name: runuser -l postgres -s /bin/bash -c '/opt/salt/scripts/rotate/rotate_db_secrets.sh finalize'
    - require:
      - file: /opt/salt/scripts/rotate/rotate_db_secrets.sh

restart-pgsql-if-rotation-finalized:
  service.running:
    - name: postgresql
    - watch:
      - cmd: finalize-db-secrets

{% endif %}