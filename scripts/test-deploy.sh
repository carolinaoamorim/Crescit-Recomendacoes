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
  'container inspect') [ "$MOCK_SCENARIO" != first_deploy ] ;;
  'stop recomendacoes'|'rm recomendacoes') ;;
  'run -d') echo new-container ;;
  *) echo "Unexpected Docker command: $*" >&2; exit 92 ;;
esac
MOCK
chmod +x "$test_dir/bin/docker"

export AUTH0_ISSUER=https://test.auth0.com/
export AUTH0_AUDIENCE=https://api.example.test
export FRONTEND_URL=http://localhost:5173
export IMAGE_TAG=larinhazzz/recomendacoes-ci:new
export PATH="$test_dir/bin:$PATH"

run_case() {
  export MOCK_SCENARIO="$1"
  export MOCK_LOG="$test_dir/$1.commands"
  : > "$MOCK_LOG"
  local status=0
  sh "$project_dir/scripts/deploy.sh" > "$test_dir/$1.output" 2>&1 || status=$?
  if [[ "$status" != "$2" ]]; then
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
assert_contains 'run -d -p 8081:8080 --name recomendacoes -e AUTH0_ISSUER -e AUTH0_AUDIENCE -e FRONTEND_URL larinhazzz/recomendacoes-ci:new'
[[ "$commands" == *'image rm --no-prune larinhazzz/recomendacoes-ci:old-alias'*'pull larinhazzz/recomendacoes-ci:new'*'stop recomendacoes'*'run -d'* ]]
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

export IMAGE_TAG=another/service:new
run_case invalid_repository 1
[[ -z "$commands" ]]
echo 'PASS: unexpected repositories are rejected before accessing Docker'
