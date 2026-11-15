# Commands for the iOS app. Run them from the root of the repository, for
# example `make ios-build`, or from this directory, for example `make build`.
#
# On a Mac, each command runs in place. On Linux, each command copies the tree to
# the Mac in IOS_HOST and runs the same command there over ssh, in this same
# Makefile. The settings come from .env in the root of the repository. See
# .env.example. This is the same method as the openmat monorepo.

.DEFAULT_GOAL := help

.PHONY: sync check generate build sim device clean help

help:
	@echo "sync      Linux only: copy the working tree to the Mac."
	@echo "check     Check the Mac and the .env settings for iOS builds."
	@echo "generate  Make the Xcode project from project.yml."
	@echo "build     Build the app for the iOS simulator."
	@echo "sim       Build the app and run it in the iOS simulator (IOS_SIM=\"iPhone 17\")."
	@echo "device    Build, install and start the app on the connected iPhone (IOS_DEVICE=<id>)."
	@echo "clean     Remove the build output and the generated Xcode project."

ifeq ($(shell uname -s),Darwin)

# On a Mac: the commands run in place.

SCHEME      := PhoneGolf
PROJECT     := PhoneGolf.xcodeproj
BUNDLE_ID   := com.phonegolf.app
DERIVED     := build
LOG         := $(DERIVED)/xcodebuild.log
IOS_SIM     ?= iPhone 17
IOS_DEVICE  ?=
# A non-interactive ssh session does not have the Homebrew path.
XCODEGEN    := $(or $(shell PATH=/opt/homebrew/bin:/usr/local/bin:$$PATH command -v xcodegen),xcodegen)

# Run xcodebuild with the log in a file. Show the errors on failure.
# $(1) = destination, $(2) = extra flags, $(3) = action
define xcode
	@mkdir -p $(DERIVED)
	@set -o pipefail; xcodebuild -project $(PROJECT) -scheme $(SCHEME) \
		-configuration Debug -derivedDataPath $(DERIVED) \
		-destination '$(1)' $(2) $(3) > $(LOG) 2>&1 \
		|| { grep -E 'error:' $(LOG) | sort -u || tail -40 $(LOG); echo "Build failed. Full log: iosApp/$(LOG)"; exit 1; }
endef

sync:
	@echo "On a Mac, there is nothing to copy."

check:
	@./scripts/check

generate:
	@command -v $(XCODEGEN) >/dev/null || { echo "xcodegen is not installed: brew install xcodegen"; exit 1; }
	$(XCODEGEN) generate --spec project.yml --quiet

build: generate
	$(call xcode,generic/platform=iOS Simulator,,build)
	@echo "Built: $(DERIVED)/Build/Products/Debug-iphonesimulator/$(SCHEME).app"

sim: build
	@set -e; \
	ID=$$(xcrun simctl list devices available | grep -F "$(IOS_SIM) (" | head -1 | grep -oE '[0-9A-F]{8}-([0-9A-F]{4}-){3}[0-9A-F]{12}'); \
	[ -n "$$ID" ] || { echo "No simulator named '$(IOS_SIM)'. Set IOS_SIM to a name from: xcrun simctl list devices available"; exit 1; }; \
	xcrun simctl boot "$$ID" 2>/dev/null || true; \
	xcrun simctl install "$$ID" $(DERIVED)/Build/Products/Debug-iphonesimulator/$(SCHEME).app; \
	xcrun simctl launch "$$ID" $(BUNDLE_ID); \
	open -a Simulator

device: generate
	@mkdir -p $(DERIVED)
	@{ [ -n "$(IOS_DEVICE)" ] && echo "$(IOS_DEVICE)" || ./scripts/device-id; } > $(DERIVED)/device-id
	$(call xcode,generic/platform=iOS,-allowProvisioningUpdates,build)
	xcrun devicectl device install app --device $$(cat $(DERIVED)/device-id) $(DERIVED)/Build/Products/Debug-iphoneos/$(SCHEME).app
	xcrun devicectl device process launch --terminate-existing --device $$(cat $(DERIVED)/device-id) $(BUNDLE_ID)

clean:
	rm -rf build PhoneGolf.xcodeproj

else

# Not on a Mac: the commands run on the Mac in IOS_HOST.

sync check generate build sim device clean:
	@./scripts/remote $@

endif
