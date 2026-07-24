CARGO ?= cargo
PREFIX ?= /usr/local
DESTDIR ?=
LIBDIR ?= $(PREFIX)/lib
INCLUDEDIR ?= $(PREFIX)/include
PKGCONFIGDIR ?= $(LIBDIR)/pkgconfig
SERVER_LIBRARY := target/release/libbiometric_sdk.so
ANDROID_LIBS_DIR ?= target/android-libs

.PHONY: android-libs check clean install-server-lib server-lib test uniffi-lib verify-uniffi-bindings

check:
	$(CARGO) fmt --all --check
	$(CARGO) clippy --locked --all-targets --all-features -- -D warnings
	RUSTDOCFLAGS="-D warnings" $(CARGO) doc --locked --no-deps --document-private-items --all-features

test:
	$(CARGO) test --locked
	$(CARGO) test --locked --no-default-features --features uniffi-bindings
	$(CARGO) test --locked --no-default-features --features server-ffi

server-lib:
	$(CARGO) build --locked --release --no-default-features --features server-ffi

install-server-lib: server-lib
	install -d "$(DESTDIR)$(LIBDIR)" "$(DESTDIR)$(INCLUDEDIR)" "$(DESTDIR)$(PKGCONFIGDIR)"
	install -m 0755 "$(SERVER_LIBRARY)" "$(DESTDIR)$(LIBDIR)/libbiometric_sdk.so"
	install -m 0644 include/biometric_sdk.h "$(DESTDIR)$(INCLUDEDIR)/biometric_sdk.h"
	sed -e 's|@PREFIX@|$(PREFIX)|g' -e 's|@LIBDIR@|$(LIBDIR)|g' \
		packaging/biometric-sdk.pc.in > "$(DESTDIR)$(PKGCONFIGDIR)/biometric-sdk.pc"

uniffi-lib:
	$(CARGO) build --locked --release --no-default-features --features uniffi-bindings

verify-uniffi-bindings: uniffi-lib
	tmp_dir="$$(mktemp -d)"; \
	$(CARGO) run --locked --no-default-features --features binding-generator --bin uniffi-bindgen -- \
		generate "$(SERVER_LIBRARY)" --language kotlin --out-dir "$$tmp_dir" --no-format; \
	generated_file="$$tmp_dir/com/datainfosec/biometric/biometric_sdk.kt"; \
	test -s "$$generated_file"; \
	grep -Fq 'open class MobileBiometricSdk' "$$generated_file"; \
	grep -Fq 'fun `sync`(): MobileSyncReport' "$$generated_file"; \
	grep -Fq 'fun `enrollSubject`' "$$generated_file"; \
	grep -Fq 'fun `enrollmentReadiness`' "$$generated_file"

android-libs:
	$(CARGO) ndk \
		-t arm64-v8a \
		-t armeabi-v7a \
		-t x86_64 \
		-o "$(ANDROID_LIBS_DIR)" \
		build --release --locked --no-default-features --features uniffi-bindings

clean:
	$(CARGO) clean
