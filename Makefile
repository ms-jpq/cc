MAKEFLAGS += --jobs
MAKEFLAGS += --no-builtin-rules
MAKEFLAGS += --warn-undefined-variables
SHELL := bash
.DELETE_ON_ERROR:
.ONESHELL:
.SHELLFLAGS := -Eeuo pipefail -O dotglob -O failglob -O globstar -c

.DEFAULT_GOAL := dev

.PHONY: clean clobber

clean:
	rm -rf -- ./.clj-kondo ./.cpcache ./.lsp ./out ./target

clobber: clean
	rm -rf -- ./node_modules

include makelib/*.mk

.PHONY: init

init:
	npm install
