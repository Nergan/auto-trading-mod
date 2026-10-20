#!/usr/bin/env bash
# Зависимости Fabric 26.3. Kotlin for Forge и Placeholder API этому моду не нужны.
# Аргументы: <каталог> <Fabric API> <Fabric Language Kotlin> <Cloth Config> <Mod Menu>
set -euo pipefail

OUT_DIR="${1:?destination directory}"
FABRIC_API_VERSION="${2:?fabric-api version_number}"
FABRIC_KOTLIN_VERSION="${3:?fabric-language-kotlin version_number}"
CLOTH_VERSION="${4:?cloth-config version_number}"
MODMENU_VERSION="${5:?modmenu version_number}"
USER_AGENT="${MODRINTH_USER_AGENT:-Nergan/auto-trading-mod (https://github.com/Nergan/auto-trading-mod)}"

mkdir -p "${OUT_DIR}"

fetch_modrinth() {
  local slug="$1"
  local version="$2"
  local json url filename sha512

  json="$(curl -fsSL --retry 5 --retry-delay 5 --retry-all-errors -A "${USER_AGENT}" "https://api.modrinth.com/v2/project/${slug}/version")"
  url="$(echo "${json}" | jq -r --arg v "${version}" '
    first(.[] | select(.version_number == $v) | .files[] | select(.primary) | .url) // empty
  ')"
  filename="$(echo "${json}" | jq -r --arg v "${version}" '
    first(.[] | select(.version_number == $v) | .files[] | select(.primary) | .filename) // empty
  ')"
  sha512="$(echo "${json}" | jq -r --arg v "${version}" '
    first(.[] | select(.version_number == $v) | .files[] | select(.primary) | .hashes.sha512) // empty
  ')"

  if [[ -z "${url}" || -z "${filename}" || -z "${sha512}" ]]; then
    echo "No primary Modrinth file for ${slug} version ${version}" >&2
    exit 1
  fi

  curl -fsSL --retry 5 --retry-delay 5 --retry-all-errors -A "${USER_AGENT}" -o "${OUT_DIR}/${filename}" "${url}"
  echo "${sha512}  ${OUT_DIR}/${filename}" | sha512sum -c -
}

fetch_modrinth "fabric-api" "${FABRIC_API_VERSION}"
fetch_modrinth "fabric-language-kotlin" "${FABRIC_KOTLIN_VERSION}"
fetch_modrinth "cloth-config" "${CLOTH_VERSION}"
fetch_modrinth "modmenu" "${MODMENU_VERSION}"
