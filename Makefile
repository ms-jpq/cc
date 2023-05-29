MAKEFLAGS += --jobs
MAKEFLAGS += --no-builtin-rules
MAKEFLAGS += --warn-undefined-variables
SHELL := bash
.DELETE_ON_ERROR:
.ONESHELL:
.RECIPEPREFIX = >
.SHELLFLAGS := -Eeuo pipefail -O dotglob -O failglob -O globstar -c

.DEFAULT_GOAL := dev

.PHONY: clean clobber

clean:
> rm -rf -- ./.clj-kondo ./.cpcache ./.lsp ./out ./target

clobber: clean
> rm -rf -- ./node_modules

.PHONY: init

init:
> npm install

.PHONY: fmt prettier
fmt: prettier

prettier:
> npx --yes -- prettier --write -- .

.PHONY: dev dev-clj dev-css

dev: dev-env dev-clj dev-css

target/public/index.html: clj/dev/index.html
> mkdir --parents -- target/public
> cp --force -- $< $@

dev-env:
> mkdir --parents -- target/public/css

dev-clj: dev-env target/public/index.html
> clojure -M:dev

dev-css: dev-env
> node_modules/.bin/tailwindcss --watch --input ./css/site.scss --output ./target/public/css/site.css

