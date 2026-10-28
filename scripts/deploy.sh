#!/bin/sh
set -eu

: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${AUTH0_ISSUER:?AUTH0_ISSUER is required}"
: "${AUTH0_AUDIENCE:?AUTH0_AUDIENCE is required}"
: "${FRONTEND_URL:?FRONTEND_URL is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

DOCKER_NETWORK=${DOCKER_NETWORK:-portfolio-network}
DB_HOST=${DB_HOST:-portfolio-postgres}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-recomendacoesdb}
DB_USER=${DB_USER:-portfolio}
export DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD
PERFIL_SERVICE_URL=${PERFIL_SERVICE_URL:-http://perfilinvestidor:8080}
runtime_env_file=${RABBITMQ_ENV_FILE:-/home/ubuntu/crescit-runtime/rabbitmq.env}
if [ -r "$runtime_env_file" ]; then . "$runtime_env_file"; fi
RABBITMQ_HOST=${RABBITMQ_HOST:-crescit-rabbitmq}
RABBITMQ_USERNAME=${RABBITMQ_USERNAME:-crescit}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD:-guest}
export PERFIL_SERVICE_URL RABBITMQ_HOST RABBITMQ_USERNAME RABBITMQ_PASSWORD

REPOSITORY=larinhazzz/recomendacoes-ci
case "$IMAGE_TAG" in
  "$REPOSITORY":?*) ;;
  *) echo "Unexpected image repository: $IMAGE_TAG" >&2; exit 1 ;;
esac

disk_usage() {
  df -h / /var/lib/docker /var/lib/containerd 2>/dev/null || true
  df -i / /var/lib/docker /var/lib/containerd 2>/dev/null || true
  docker system df
}

echo 'Disk usage before cleaning unused Recommendations images:'
disk_usage

# Each commit publishes a new tag. Remove only this repository's unused tags.
# Any container, including a stopped one, protects its image from this cleanup.
references=$(docker image ls "$REPOSITORY" --format '{{.Repository}}:{{.Tag}}')
printf '%s\n' "$references" | while IFS= read -r reference; do
  case "$reference" in
    "$REPOSITORY":?*) ;;
    *) continue ;;
  esac
  if [ "$reference" = "$IMAGE_TAG" ] || [ "$reference" = "$REPOSITORY:<none>" ]; then
    continue
  fi
  image_id=$(docker image inspect --format '{{.Id}}' "$reference")
  containers=$(docker container ls --all --quiet --filter "ancestor=$image_id")
  if [ -n "$containers" ]; then
    echo "Keeping image used by a container: $reference"
    continue
  fi
  echo "Removing unused Recommendations image: $reference"
  docker image rm --no-prune "$reference"
done

# Download completely before stopping the existing service.
if [ "${PULL_IMAGE:-true}" = true ] && ! docker pull "$IMAGE_TAG"; then
  echo 'Image download failed; the current service was not stopped.' >&2
  disk_usage
  echo 'If disk space is still exhausted, review the usage above or expand the server disk.' >&2
  exit 1
fi

# Share the existing PostgreSQL container, with a separate Recommendations database.
# Check dependencies before replacing the current application container.
docker network inspect "$DOCKER_NETWORK" >/dev/null
db_networks=$(docker container inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' "$DB_HOST")
if ! printf '%s\n' "$db_networks" | grep -Fqx -- "$DOCKER_NETWORK"; then
  docker network connect "$DOCKER_NETWORK" "$DB_HOST"
fi

attempt=0
until docker exec "$DB_HOST" pg_isready -h 127.0.0.1 -p "$DB_PORT" -U "$DB_USER" -d postgres; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "PostgreSQL in $DB_HOST did not become ready; the current service was not stopped." >&2
    exit 1
  fi
  sleep 2
done

# psql quotes database and owner identifiers; existing databases are preserved.
PGPASSWORD=$DB_PASSWORD docker exec -i -e PGPASSWORD "$DB_HOST" \
  psql -X -h 127.0.0.1 -p "$DB_PORT" -U "$DB_USER" -d postgres \
  -v ON_ERROR_STOP=1 -v db_name="$DB_NAME" -v db_user="$DB_USER" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')\gexec
SQL

if docker container inspect recomendacoes >/dev/null 2>&1; then
  docker stop recomendacoes
  docker rm recomendacoes
fi
docker run -d --name recomendacoes \
  --restart unless-stopped --network "$DOCKER_NETWORK" \
  --log-opt max-size=10m --log-opt max-file=3 \
  -e AUTH0_ISSUER -e AUTH0_AUDIENCE -e FRONTEND_URL \
  -e DB_HOST -e DB_PORT -e DB_NAME -e DB_USER -e DB_PASSWORD \
  -e PERFIL_SERVICE_URL -e RABBITMQ_HOST -e RABBITMQ_USERNAME -e RABBITMQ_PASSWORD \
  "$IMAGE_TAG"

attempt=0
while [ "$attempt" -lt 60 ]; do
  if [ "$(docker container inspect --format '{{.State.Running}}' recomendacoes)" != true ]; then
    echo 'Recommendations exited before becoming healthy.' >&2
    docker logs --tail 100 recomendacoes >&2
    exit 1
  fi
  if docker exec recomendacoes wget -q --spider http://127.0.0.1:8080/health; then
    echo 'Recommendations is healthy.'
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 2
done

echo 'Recommendations did not become healthy in time.' >&2
docker logs --tail 100 recomendacoes >&2
exit 1
