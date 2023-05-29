MAKEFLAGS += --no-builtin-rules
MAKEFLAGS += --warn-undefined-variables
SHELL := bash
.DELETE_ON_ERROR:
.RECIPEPREFIX = >
.SHELLFLAGS := -Eeuo pipefail -O dotglob -O failglob -O globstar -c

.DEFAULT_GOAL := dev
.PHONY: dev

dev:
> clojure -M:dev
