.PHONY: all plugin datapack clean

DIST := build
PLUGIN := $(DIST)/pap-plugin.jar
DATAPACK := $(DIST)/pap-datapack.zip

all: plugin datapack

plugin:
	mvn --batch-mode --no-transfer-progress package
	mkdir -p $(DIST)
	cp target/pap-plugin-1.0.0.jar $(PLUGIN)

datapack:
	mkdir -p $(DIST)
	rm -f $(DATAPACK)
	cd datapack && zip -q -r ../$(DATAPACK) pack.mcmeta data

clean:
	mvn --batch-mode clean
	rm -rf $(DIST)
