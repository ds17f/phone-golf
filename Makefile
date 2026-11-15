.PHONY: help api-install api-dev api-test api-typecheck test docker-build docker-dev \
	android-build android-test android-install android-emulator \
	homelab-deploy android-build-home android-install-home

help:
	@echo "API + web:   api-install api-dev api-test api-typecheck docker-build docker-dev"
	@echo "Android:     android-build android-test android-install android-emulator"
	@echo "iOS:         ios-check ios-build ios-sim ios-device (see iosApp/Makefile)"
	@echo "All tests:   test"

# ---- API + web leaderboard ---------------------------------------------------
# Dev port is 3100 (3000 is often taken by other local apps).
DEV_PORT ?= 3100

api-install:
	cd api && npm ci

api-dev:
	cd api && PORT=$(DEV_PORT) npm run dev

api-test:
	cd api && npm test

api-typecheck:
	cd api && npm run typecheck

docker-build:
	docker build -t phone-golf:dev .

docker-dev:
	docker compose -f docker-compose.dev.yml up --build

test: api-typecheck api-test android-test

# ---- Android -----------------------------------------------------------------
# Debug builds talk to http://10.0.2.2:$(DEV_PORT) (the host, seen from the emulator).
# Release:  cd androidApp && ./gradlew assembleRelease -PapiBaseUrl=https://golf.example.com
ADB_DEVICE ?= emulator-5554

android-build:
	cd androidApp && ./gradlew assembleDebug

android-test:
	cd androidApp && ./gradlew testDebugUnitTest

android-install: android-build
	adb -s $(ADB_DEVICE) install -r androidApp/app/build/outputs/apk/debug/app-debug.apk

# SwiftShader crashes on this machine; always use the host GPU.
android-emulator:
	$$ANDROID_HOME/emulator/emulator -avd Pixel_API_36 -gpu host -no-audio &

# ---- iOS ---------------------------------------------------------------------
# `make ios-<cmd>` runs `make -C iosApp <cmd>`: sync check generate build sim
# device clean. On Linux, each command copies the tree to the Mac in IOS_HOST
# and runs there over ssh (the same method as openmat). Settings are in .env.
ios-%:
	@$(MAKE) --no-print-directory -C iosApp $*

# ---- Home server -------------------------------------------------------------
# Set HOMELAB_HOST (user@host) and GOLF_URL in .env. See .env.example.
-include .env
GOLF_URL ?= https://phonegolf.makersundays.com

homelab-deploy android-build-home: _need-homelab
_need-homelab:
	@[ -n "$(HOMELAB_HOST)" ] || { echo "Set HOMELAB_HOST in .env."; exit 1; }

homelab-deploy:
	ssh $(HOMELAB_HOST) "mkdir -p HomeLab/services/phone-golf/app"
	rsync -az --delete --exclude='.git' --exclude='node_modules' --exclude='api/data' --exclude='api/dist' \
		--exclude='androidApp' --exclude='iosApp' ./ $(HOMELAB_HOST):HomeLab/services/phone-golf/app/
	ssh $(HOMELAB_HOST) "cd HomeLab/services && make phone-golf-up"

# Debug-signed APK that talks to the home server. Install: make android-install-home ADB_DEVICE=<serial>
android-build-home:
	cd androidApp && ./gradlew assembleDebug -PapiBaseUrl=$(GOLF_URL)

android-install-home: android-build-home
	adb -s "$(ADB_DEVICE)" install -r androidApp/app/build/outputs/apk/debug/app-debug.apk
