package de.intektor.mercury.util

const val ACTION_UPLOAD_REFERENCE_PROGRESS = "de.intektor.mercury.uploadProgress"
const val ACTION_UPLOAD_REFERENCE_FINISHED = "de.intektor.mercury.uploadReferenceFinished"
const val ACTION_DOWNLOAD_REFERENCE_PROGRESS = "de.intektor.mercury.downloadReferenceFinished"
const val ACTION_DOWNLOAD_REFERENCE_FINISHED = "de.intektor.mercury.downloadReferenceFinished"
const val ACTION_UPLOAD_REFERENCE_STARTED = "de.intektor.mercury.uploadReferenceStarted"

const val ACTION_NOTIFICATION_REPLY = "de.intektor.mercury.notification.reply"

const val KEY_NOTIFICATION_ID = "de.intektor.mercury.notification.id"
const val KEY_NOTIFICATION_REPLY = "de.intektor.mercury.notification.reply.key"

const val KEY_NOTIFICATION_GROUP_ID = 0
const val KEY_NOTIFICATION_INITIALZE_CHAT_ID = 1_000_000

//Constants related to SendMediaActivity
const val KEY_CONTACT = "de.intektor.mercury.contact.key"
const val KEY_MEDIA_TYPE = "de.intektor.mercury.media_type.key"
const val KEY_CHAT_INFO = "de.intektor.mercury.chat_info.key"
const val KEY_MEDIA_URL = "de.intektor.mercury.media.url.key"
const val KEY_MESSAGE_TEXT = "de.intektor.mercury.message.text.key"

const val KEY_USER_UUID = "de.intektor.mercury.user_uuid.key"
const val KEY_FILE_URI = "de.intektor.mercury.file_uri.key"
const val KEY_MESSAGE_UUID = "de.intektor.mercury.message_uuid.key"
const val KEY_REFERENCE_UUID = "de.intektor.mercury.reference_uuid.key"

const val KEY_MEDIA_DATA = "de.intektor.mercury.media_data.key"

const val ACTION_USER_VIEW_CHAT = "de.intektor.mercury.user_view_chat.action"
const val KEY_CHAT_UUID = "de.intektor.mercury.chat_uuid.key"
const val KEY_CHAT_NAME = "de.intektor.mercury.chat_name.key"
const val KEY_CHAT_PARTICIPANTS = "de.intektor.mercury.chat_participants.key"
const val KEY_CHAT_TYPE = "de.intektor.mercury.chat_type.key"
const val KEY_USER_VIEW = "de.intektor.mercury.user_view_chat.user_view.key"

const val ACTION_DIRECT_CONNECTION_CONNECTED = "de.intektor.mercury.direct_connection_reconnected.action"

const val KEY_FOLDER = "de.intektor.mercury.folder.key"

const val ACTION_UPLOAD_PROFILE_PICTURE = "de.intektor.mercury.upload_profile_picture.action"
const val ACTION_CANCEL_UPLOAD_PROFILE_PICTURE = "de.intektor.mercury.upload_profile_picture.cancel.action"
const val KEY_PICTURE = "de.intektor.mercury.picture.key"

const val ACTION_INITIALIZE_CHAT = "de.intektor.mercury.initialize_chat.action"

const val NOTIFICATION_CHANNEL_NEW_MESSAGES = "de.intektor.mercury.notifications.new_messages.channel"
const val NOTIFICATION_CHANNEL_UPLOAD_PROFILE_PICTURE = "de.intektor.mercury.notifications.upload_profile_picture.channel"
const val NOTIFICATION_CHANNEL_UPLOAD_MEDIA = "de.intektor.mercury.notifications.upload_media.channel"
const val NOTIFICATION_CHANNEL_DOWNLOAD_MEDIA = "de.intektor.mercury.notification.download_media.channel"
const val NOTIFICATION_CHANNEL_MISC = "de.intektor.mercury.notification.misc.channel"

const val NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE = 1
const val NOTIFICATION_ID_DELETING_CHAT_MESSAGES = 990000
const val SP_DELETING_CHAT_MESSAGES = "de.intektor.mercury.notifications.deleting_chat_messages.sp"

const val ACTION_DOWNLOAD_PROFILE_PICTURE = "de.intektor.mercury.download_profile_picture.action"
const val KEY_PROFILE_PICTURE_TYPE = "de.intektor.mercury.download_profile_picture.type.key"

const val ACTION_PROFILE_PICTURE_UPDATED = "de.intektor.mercury.profile_picture_updated.action"
const val ACTION_PROFILE_PICTURE_UPLOADED = "de.intektor.mercury.profile_picture_uploaded.action"

const val ACTION_DOWNLOAD_NINE_GAG = "de.intektor.mercury.download_nine_gag.action"
const val KEY_NINE_GAG_ID = "de.intektor.mercury.download_nine_gag.gag_id.key"
const val KEY_NINE_GAG_UUID = "de.intektor.mercury.download_nine_gag.gag_uuid.key"

const val ACTION_CANCEL_DOWNLOAD_MEDIA = "de.intektor.mercury.cancel_download_media.action"

const val KEY_PROGRESS = "de.intektor.mercury.progress.key"
const val KEY_SUCCESSFUL = "de.intektor.mercury.successful.key"

const val ACTION_UPLOAD_REFERENCE = "de.intektor.mercury.upload_reference.action"

const val SHARED_PREFERENCES_THEME = "de.intektor.mercury.theme.sp"
const val SP_IS_LIGHT_THEME_KEY = "de.intektor.mercury.theme.key.sp"

const val SHARED_PREFERENCES_NOTIFICATIONS = "de.intektor.mercury.notifications.sp"
const val SP_NOTIFICATIONS_KEY = "de.intektor.mercury.notifications.key.sp"

const val KENTAI_NEW_MESSAGES_GROUP_KEY = "de.intektor.mercury.new_notifications.group_key.key"

const val ACTION_INIT_CHAT_FINISHED = "de.intektor.mercury.chat_initialization_finished.action"

const val ACTION_SEND_MESSAGES = "de.intektor.mercury.send_messages.action"
const val KEY_AMOUNT = "de.intektor.mercury.amount.key"
const val KEY_RECEIVER = "de.intektor.mercury.receiver.key"

const val ACTION_GROUP_MODIFICATION_RECEIVED = "de.intektor.mercury.group_modification_received.action"
const val KEY_GROUP_MODIFICATION_UUID = "de.intektor.mercury.modification_uuid.action"
const val KEY_GROUP_MODIFICATION = "de.intektor.mercury.group_modification.action"
const val KEY_GROUP_MODIFICATION_TYPE_ID = "de.intektor.mercury.group_modification_type_id.key"

const val ACTION_MESSAGE_STATUS_CHANGE = "de.intektor.mercury.message_status_change.action"
const val KEY_MESSAGE_STATUS = "de.intektor.mercury.message_status.key"
const val KEY_TIME = "de.intektor.mercury.time.key"

const val ACTION_CHAT_NOTIFICATION = "de.intektor.mercury.chat_notification.action"
const val KEY_UNREAD_MESSAGES = "de.intektor.mercury.unread_messages.key"
const val KEY_ADDITIONAL_INFO_REGISTRY_ID = "de.intektor.mercury.additional_info_registry_id.key"
const val KEY_ADDITIONAL_INFO_CONTENT = "de.intektor.mercury.additional_info_content.key"
const val KEY_MESSAGE_REGISTRY_ID = "de.intektor.mercury.message_registry_id.key"

const val SP_CAMERA_SETTINGS = "de.intektor.mercury.camera_settings.sp"
const val KEY_CAMERA_SETTINGS_FLASH_MODE = "de.intektor.mercury.camera_settings.flash_mode.key"
const val CAMERA_SETTINGS_FLASH_OFF = 0
const val CAMERA_SETTINGS_FLASH_AUTO = 1
const val CAMERA_SETTINGS_FLASH_ON = 2