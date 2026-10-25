#!/bin/sh
# Build the two copies of the app from app.html (the artifact source):
#   index.html                              - standalone page to open in a browser
#   android/app/src/main/assets/index.html  - the Android app's page, using bundled fonts
set -e
cd "$(dirname "$0")"
head='<!doctype html>\n<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body style="margin:0">\n'
foot='\n</body></html>\n'
{ printf "$head"; cat app.html; printf "$foot"; } > index.html
{ printf "$head"; grep -v 'fonts.googleapis.com\|fonts.gstatic.com' app.html | sed '1a <link rel="stylesheet" href="fonts/fonts.css">'; printf "$foot"; } > android/app/src/main/assets/index.html
