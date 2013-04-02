#!/bin/bash

# release riemann crate

if [[ $# -lt 3 ]]; then
  echo "usage: $(basename $0) previous-version new-version next-version" >&2
  exit 1
fi

previous_version=$1
version=$2
next_version=$3

echo ""
echo "Start release of $version, previous version is $previous_version"
echo ""
echo ""

lein do clean, test && \
git flow release start $version || exit 1

lein with-profile +release set-version ${version} :previous-version ${previous_version} \
  || { echo "set version failed" >2 ; exit 1; }

echo ""
echo ""
echo "Changes since $previous_version"
git --no-pager log --pretty=changelog $previous_version..
echo ""
echo ""

$EDITOR resources/pallet_crate/riemann_crate/meta.edn
$EDITOR doc-src/USAGE.md
git add resources/pallet_crate/riemann_crate/meta.edn doc-src/USAGE.md doc-src/FOOTER.md \
&& git commit -m "Updated metadata for $version" \
&& lein crate-doc \
|| exit 1

echo "Now edit project.clj, ReleaseNotes and README"

$EDITOR project.clj
$EDITOR ReleaseNotes.md
$EDITOR README.md

echo -n "commiting project.clj, release notes and readme.  enter to continue:" \
&& read x \
&& git add project.clj ReleaseNotes.md README.md \
&& git commit -m "Updated project.clj, release notes and readme for $version" \
&& echo -n "Peform release.  enter to continue:" && read x \
&& lein do clean, install, test, deploy clojars \
&& rm pom.xml \
&& git flow release finish $version \
&& echo "Now push to github. Don't forget the tags!" \
&& lein with-profile +doc doc \
&& lein with-profile +release set-version ${next_version} \
&& git add project.clj \
&& git commit -m "Updated version for next release cycle"
