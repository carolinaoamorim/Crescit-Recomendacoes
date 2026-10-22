#!/bin/sh
set -eu

: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${AUTH0_ISSUER:?AUTH0_ISSUER is required}"
: "${AUTH0_AUDIENCE:?AUTH0_AUDIENCE is required}"
: "${FRONTEND_URL:?FRONTEND_URL is required}"

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
if ! docker pull "$IMAGE_TAG"; then
  echo 'Image download failed; the current service was not stopped.' >&2
  disk_usage
  echo 'If disk space is still exhausted, review the usage above or expand the server disk.' >&2
  exit 1
fi

if docker container inspect recomendacoes >/dev/null 2>&1; then
  docker stop recomendacoes
  docker rm recomendacoes
fi
docker run -d -p 8081:8080 --name recomendacoes \
  -e AUTH0_ISSUER -e AUTH0_AUDIENCE -e FRONTEND_URL \
  "$IMAGE_TAG"
