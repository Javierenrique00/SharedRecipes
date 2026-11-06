package com.co.recipes.infraestructure.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS secret store backed by the Keychain, one `kSecClassGenericPassword` item per key.
 *
 * The Keychain encrypts the payload with a hardware-backed class key, so unlike the
 * Android/desktop paths this implementation performs no cryptography of its own.
 *
 * Items use `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: readable once the
 * device has been unlocked after a reboot (so background token refresh works), never
 * synced to iCloud, and never restored onto another device.
 */
@OptIn(ExperimentalForeignApi::class)
internal class KeychainSecureStore(
    private val service: String = NSBundle.mainBundle.bundleIdentifier ?: DEFAULT_SERVICE,
) : SecureStore {

    override fun get(key: String): String? {
        val query = baseQuery(key)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        return try {
            memScoped {
                val result = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return@memScoped null
                val data = result.value ?: return@memScoped null
                val nsData = CFBridgingRelease(data) as? NSData ?: return@memScoped null
                NSString.create(data = nsData, encoding = NSUTF8StringEncoding) as String?
            }
        } finally {
            CFRelease(query)
        }
    }

    override fun put(key: String, value: String) {
        val nsData = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val dataRef = CFBridgingRetain(nsData) ?: return
        val attributes = baseQuery(key)
        CFDictionaryAddValue(attributes, kSecValueData, dataRef)
        CFDictionaryAddValue(attributes, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        try {
            if (SecItemAdd(attributes, null) == errSecDuplicateItem) {
                updateValue(key, dataRef)
            }
        } finally {
            CFRelease(attributes)
            CFBridgingRelease(dataRef)
        }
    }

    override fun remove(key: String) {
        val query = baseQuery(key)
        try {
            SecItemDelete(query)
        } finally {
            CFRelease(query)
        }
    }

    private fun updateValue(key: String, dataRef: CFTypeRef) {
        val query = baseQuery(key)
        val changes = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            1,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        CFDictionaryAddValue(changes, kSecValueData, dataRef)
        try {
            SecItemUpdate(query, changes)
        } finally {
            CFRelease(query)
            CFRelease(changes)
        }
    }

    /**
     * Builds the class/service/account query. The bridged CFStrings are released right
     * after insertion because the dictionary retains its values.
     */
    private fun baseQuery(key: String): CFDictionaryRef {
        val dictionary = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            3,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )!!
        val serviceRef = CFBridgingRetain(NSString.create(string = service))
        val accountRef = CFBridgingRetain(NSString.create(string = key))
        CFDictionaryAddValue(dictionary, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dictionary, kSecAttrService, serviceRef)
        CFDictionaryAddValue(dictionary, kSecAttrAccount, accountRef)
        serviceRef?.let { CFBridgingRelease(it) }
        accountRef?.let { CFBridgingRelease(it) }
        return dictionary
    }

    private companion object {
        const val DEFAULT_SERVICE = "com.co.recipes"
    }
}

internal actual fun createSecureStore(): SecureStore = KeychainSecureStore()
