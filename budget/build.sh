#!/bin/sh
# Wrap app.html (the artifact source) into a standalone page you can open directly in a browser.
cd "$(dirname "$0")"
{
  printf '<!doctype html>\n<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body style="margin:0">\n'
  cat app.html
  printf '\n</body></html>\n'
} > index.html
