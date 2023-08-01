MAKEFLAGS += --jobs
MAKEFLAGS += --no-builtin-rules
MAKEFLAGS += --warn-undefined-variables
SHELL := bash
.DELETE_ON_ERROR:
.ONESHELL:
.SHELLFLAGS := --norc --noprofile -Eeuo pipefail -O dotglob -O failglob -O globstar -c

.DEFAULT_GOAL := dev

.PHONY: clean clobber

clean:
	rm -v -rf -- ./.clj-kondo ./.cpcache ./.lsp ./out ./target

clobber: clean
	rm -v -rf -- ./node_modules

include makelib/*.mk

.PHONY: init

init:
	npm install
