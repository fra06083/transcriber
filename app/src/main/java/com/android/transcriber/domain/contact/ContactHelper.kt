package com.android.transcriber.domain.contact

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.android.transcriber.service.WhatsAppAutoSendService

data class ContactItem(
    val id: String,
    val name: String,
    val number: String,
    val cleanNumber: String,
    val photoUri: String? = null
)

object ContactHelper {

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun cleanForWhatsApp(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() || it == '+' }
        var clean = digitsOnly.replace("+", "")
        if (clean.startsWith("00")) {
            clean = clean.substring(2)
        }
        // Italian mobile number without prefix (10 digits starting with 3)
        if (clean.length == 10 && clean.startsWith("3")) {
            clean = "39$clean"
        }
        return clean.trim()
    }

    fun formatForDisplay(phone: String): String {
        val clean = cleanForWhatsApp(phone)
        if (clean.startsWith("39") && clean.length == 12) {
            // +39 3XX XXXXXXX
            return "+39 ${clean.substring(2, 5)} ${clean.substring(5)}"
        }
        return if (phone.startsWith("+")) phone else "+$clean"
    }

    fun searchContacts(context: Context, query: String = ""): List<ContactItem> {
        if (!hasContactsPermission(context)) return emptyList()

        val results = mutableListOf<ContactItem>()
        val contentResolver = context.contentResolver

        val trimmedQuery = query.trim()
        val uri: Uri
        val selection: String?
        val selectionArgs: Array<String>?

        if (trimmedQuery.isNotBlank()) {
            uri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(trimmedQuery)
            )
            selection = null
            selectionArgs = null
        } else {
            uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            selection = null
            selectionArgs = null
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                val seenNumbers = mutableSetOf<String>()

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null

                    val cleanNum = cleanForWhatsApp(rawNumber)
                    if (cleanNum.isNotBlank() && seenNumbers.add(cleanNum)) {
                        results.add(
                            ContactItem(
                                id = id,
                                name = name.ifBlank { rawNumber },
                                number = rawNumber.trim(),
                                cleanNumber = cleanNum,
                                photoUri = photo
                            )
                        )
                    }
                    if (results.size >= 80) break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results
    }

    fun getContactFromUri(context: Context, contactUri: Uri): ContactItem? {
        val contentResolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        try {
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val rawNum = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null

                    val cleanNum = cleanForWhatsApp(rawNum)
                    return ContactItem(
                        id = contactUri.toString(),
                        name = name.ifBlank { rawNum },
                        number = rawNum.trim(),
                        cleanNumber = cleanNum,
                        photoUri = photo
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, WhatsAppAutoSendService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
