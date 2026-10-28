#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
test_dir="$project_dir/target/deploy-script-tests"
mkdir -p "$test_dir/bin"

# The deploy script sees only this fake CLI. No Docker daemon or SSH is used.
cat > "$test_dir/bin/docker" <<'MOCK'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> "$MOCK_LOG"
case "$1 $2" in
  'system df') echo 'Mock disk usage'; exit 0 ;;
  'image ls')
    [ "$MOCK_SCENARIO" != list_failure ] || exit 1
    [ "$MOCK_SCENARIO" != first_deploy ] || exit 0
    printf '%s\n' \
      'larinhazzz/recomendacoes-ci:active' \
      'larinhazzz/recomendacoes-ci:stopped' \
      'larinhazzz/recomendacoes-ci:old' \
      'larinhazzz/recomendacoes-ci:old-alias' \
      'larinhazzz/recomendacoes-ci:new' \
      'larinhazzz/recomendacoes-ci:<none>' \
      'another/service:old'
    ;;
  'image inspect')
    case "$5" in
      *:active) echo sha256:active ;;
      *:stopped) echo sha256:stopped ;;
      *:old|*:old-alias) echo sha256:old ;;
      *) exit 90 ;;
    esac
    ;;
  'container ls')
    case "$6" in
      ancestor=sha256:active) echo running-container ;;
      ancestor=sha256:stopped) echo stopped-container ;;
      ancestor=sha256:old) ;;
      *) exit 91 ;;
    esac
    ;;
  'image rm') [ "$MOCK_SCENARIO" != cleanup_failure ] ;;
  'pull larinhazzz/recomendacoes-ci:new')
    if [ "$MOCK_SCENARIO" = pull_failure ]; then
      echo 'no space left on device' >&2
      exit 1
    fi
    ;;
  'network inspect') [ "$MOCK_SCENARIO" != network_failure ] ;;
  'network connect') [ "$MOCK_SCENARIO" != network_connect_failure ] ;;
  'container inspect')
    if [ "$#" -eq 3 ] && [ "$3" = recomendacoes ]; then
      [ "$MOCK_SCENARIO" != first_deploy ]
    elif [ "$4" = '{{.State.Running}}' ]; then
      if [ "$MOCK_SCENARIO" = startup_failure ]; then echo false; else echo true; fi
    else
      [ "$5" = "$DB_HOST" ] || exit 93
      [ "$MOCK_SCENARIO" != db_missing ] || exit 1
      case "$MOCK_SCENARIO" in
        attach_network|network_connect_failure) echo bridge ;;
        *) echo "$DOCKER_NETWORK" ;;
      esac
    fi
    ;;
  "exec $DB_HOST")
    [ "$3" = pg_isready ] || exit 94
    [ "$MOCK_SCENARIO" != db_not_ready ]
    ;;
  'exec -i')
    [ "$PGPASSWORD" = "$DB_PASSWORD" ] || exit 95
    cat > "$MOCK_LOG.sql"
    [ "$MOCK_SCENARIO" != db_setup_failure ]
    ;;
  'exec recomendacoes')
    [ "$3" = wget ] || exit 96
    [ "$MOCK_SCENARIO" != health_timeout ] || exit 1
    if [ "$MOCK_SCENARIO" = health_pending ] && [ "$(cat "$MOCK_LOG.health-pending")" = 0 ]; then
      printf '1\n' > "$MOCK_LOG.health-pending"
      exit 1
    fi
    ;;
  'logs --tail') echo 'Mock application startup logs' ;;
  'stop recomendacoes'|'rm recomendacoes') ;;
  'run -d')
    printf '%s\n' "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" > "$MOCK_LOG.environment"
    echo new-container
    ;;
  *) echo "Unexpected Docker command: $*" >&2; exit 92 ;;
esac
MOCK
chmod +x "$test_dir/bin/docker"
printf '#!/bin/sh\nexit 0\n' > "$test_dir/bin/sleep"
chmod +x "$test_dir/bin/sleep"

export AUTH0_ISSUER=https://test.auth0.com/
export AUTH0_AUDIENCE=https://api.example.test
export FRONTEND_URL=http://localhost:5173
export IMAGE_TAG=larinhazzz/recomendacoes-ci:new
export DOCKER_NETWORK=portfolio-network
export DB_HOST=portfolio-postgres
export DB_PORT=5432
export DB_NAME=recomendacoesdb
export DB_USER=portfolio
export DB_PASSWORD='test password $with "quotes" and spaces'
export PATH="$test_dir/bin:$PATH"

run_case() {
  export MOCK_SCENARIO="$1"
  export MOCK_LOG="$test_dir/$1.commands"
  : > "$MOCK_LOG"
  # Reset the fake health probe when re-running the same case.
  printf '0\n' > "$MOCK_LOG.health-pending"
  local status=0
  sh "$project_dir/scripts/deploy.sh" > "$test_dir/$1.output" 2>&1 || status=$?
  if [[ "$2" == nonzero ]]; then
    if [[ "$status" == 0 ]]; then
      cat "$test_dir/$1.output"
      echo "$1: expected a failure, received exit 0" >&2
      exit 1
    fi
  elif [[ "$status" != "$2" ]]; then
    cat "$test_dir/$1.output"
    echo "$1: expected exit $2, received $status" >&2
    exit 1
  fi
  commands=$(cat "$MOCK_LOG")
}

assert_contains() {
  [[ "$commands" == *"$1"* ]] || { echo "Missing command: $1" >&2; exit 1; }
}
assert_absent() {
  [[ "$commands" != *"$1"* ]] || { echo "Unexpected command: $1" >&2; exit 1; }
}
assert_no_service_change() {
  assert_absent 'stop recomendacoes'
  assert_absent 'rm recomendacoes'
  assert_absent 'run -d'
}

run_case success 0
assert_contains 'image rm --no-prune larinhazzz/recomendacoes-ci:old'
assert_contains 'image rm --no-prune larinhazzz/recomendacoes-ci:old-alias'
assert_absent 'image rm --no-prune larinhazzz/recomendacoes-ci:active'
assert_absent 'image rm --no-prune larinhazzz/recomendacoes-ci:stopped'
assert_absent 'image rm --no-prune larinhazzz/recomendacoes-ci:new'
assert_absent 'image inspect --format {{.Id}} another/service:old'
assert_absent 'prune -'
assert_absent 'volume '
assert_contains 'container ls --all --quiet --filter ancestor=sha256:stopped'
assert_contains 'run -d -p 8081:8080 --name recomendacoes --restart unless-stopped --network portfolio-network -e AUTH0_ISSUER -e AUTH0_AUDIENCE -e FRONTEND_URL -e DB_HOST -e DB_PORT -e DB_NAME -e DB_USER -e DB_PASSWORD larinhazzz/recomendacoes-ci:new'
assert_contains 'network inspect portfolio-network'
assert_absent 'network connect'
assert_contains 'exec portfolio-postgres pg_isready -h 127.0.0.1 -p 5432 -U portfolio -d postgres'
assert_contains 'exec -i -e PGPASSWORD portfolio-postgres psql -X -h 127.0.0.1 -p 5432 -U portfolio -d postgres -v ON_ERROR_STOP=1 -v db_name=recomendacoesdb -v db_user=portfolio'
assert_contains 'exec recomendacoes wget -q --spider http://127.0.0.1:8080/health'
assert_absent "$DB_PASSWORD"
[[ "$(cat "$MOCK_LOG.environment")" == $'portfolio-postgres\n5432\nrecomendacoesdb\nportfolio' ]]
sql=$(cat "$MOCK_LOG.sql")
[[ "$sql" == *"CREATE DATABASE %I OWNER %I"* && "$sql" == *"WHERE NOT EXISTS"* && "$sql" == *'\gexec'* ]]
[[ "$commands" == *'image rm --no-prune larinhazzz/recomendacoes-ci:old-alias'*'pull larinhazzz/recomendacoes-ci:new'*'stop recomendacoes'*'run -d'* ]]
[[ "$commands" == *'pg_isready'*'psql -X'*'stop recomendacoes'*'run -d'*'exec recomendacoes wget'* ]]
echo 'PASS: cleanup is scoped, protects used images, and precedes pull and replacement'

run_case first_deploy 0
assert_contains 'pull larinhazzz/recomendacoes-ci:new'
assert_contains 'run -d'
assert_absent 'image rm '
assert_absent 'stop recomendacoes'
assert_absent 'rm recomendacoes'
echo 'PASS: first deployment works without existing images or container'

run_case pull_failure 1
assert_contains 'pull larinhazzz/recomendacoes-ci:new'
assert_no_service_change
echo 'PASS: failed pull keeps the existing service'

run_case cleanup_failure 1
assert_absent 'pull '
assert_no_service_change
echo 'PASS: cleanup errors abort before deployment'

run_case list_failure 1
assert_absent 'image rm '
assert_absent 'pull '
assert_no_service_change
echo 'PASS: Docker inspection errors abort before deleting anything'

run_case attach_network 0
assert_contains 'network connect portfolio-network portfolio-postgres'
echo 'PASS: PostgreSQL joins the application network when needed'

for scenario in network_failure network_connect_failure db_missing db_not_ready db_setup_failure; do
  run_case "$scenario" 1
  assert_no_service_change
done
echo 'PASS: network and database failures keep the existing application container'

run_case health_pending 0
[[ "$(cat "$MOCK_LOG.health-pending")" == 1 ]]
assert_absent 'logs --tail'
echo 'PASS: deployment waits for the application health endpoint'

for scenario in startup_failure health_timeout; do
  run_case "$scenario" 1
  assert_contains 'run -d'
  assert_contains 'logs --tail 100 recomendacoes'
done
echo 'PASS: application startup failures and health timeouts fail deployment with logs'

export DOCKER_NETWORK=custom-network DB_HOST=custom-postgres DB_PORT=5433 DB_NAME=custom_recommendations DB_USER=custom_user
run_case custom_database 0
assert_contains '--network custom-network'
assert_contains 'exec custom-postgres pg_isready -h 127.0.0.1 -p 5433 -U custom_user -d postgres'
assert_contains '-v db_name=custom_recommendations -v db_user=custom_user'
[[ "$(cat "$MOCK_LOG.environment")" == $'custom-postgres\n5433\ncustom_recommendations\ncustom_user' ]]
echo 'PASS: configured database and network values reach the application'

unset DB_PASSWORD
run_case missing_password nonzero
[[ -z "$commands" ]]
export DB_PASSWORD=test-password
echo 'PASS: a missing database password aborts before accessing Docker'

export IMAGE_TAG=another/service:new
run_case invalid_repository 1
[[ -z "$commands" ]]
echo 'PASS: unexpected repositories are rejected before accessing Docker'
