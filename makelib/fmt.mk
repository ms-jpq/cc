.PHONY: fmt prettier
fmt: prettier fmt-clj

fmt-clj:
	clojure -T:fmt fix

prettier:
	npx --yes -- prettier --write -- .
