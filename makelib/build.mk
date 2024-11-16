.PHONY: build

out/web:
	mkdir -p -- '$@'

out/web/site.css: $(shell shopt -u failglob && printf -- '%s ' ./clj/**/*.clj ./css/*) | out/web
	node_modules/.bin/tailwindcss --minify --input css/site.scss --output '$@'

out/web/bundle.js: $(shell shopt -u failglob && printf -- '%s ' ./*.edn ./clj/**/*.clj{c,s}) | out/web
	clojure -M:build-cljs

out/compiled.jar: out/web/site.css out/web/bundle.js $(shell shopt -u failglob && printf -- '%s ' ./*.{clj,edn} ./clj/**/*.cl{j,jc})
	clojure -T:build-clj uber

out/uber.jar: out/compiled.jar
	{
		printf -- '%s\n' '#!/usr/bin/env -S -- java -jar'
		cat -- '$<'
	} > '$@'
	chmod +x -- '$@'

build: out/uber.jar
