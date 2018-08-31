package de.intektor.kentai.kentai

const val ACTION_UPLOAD_REFERENCE_PROGRESS = "de.intektor.kentai.uploadProgress"
const val ACTION_UPLOAD_REFERENCE_FINISHED = "de.intektor.kentai.uploadReferenceFinished"
const val ACTION_DOWNLOAD_REFERENCE_PROGRESS = "de.intektor.kentai.downloadReferenceFinished"
const val ACTION_DOWNLOAD_REFERENCE_FINISHED = "de.intektor.kentai.downloadReferenceFinished"
const val ACTION_UPLOAD_REFERENCE_STARTED = "de.intektor.kentai.uploadReferenceStarted"

const val ACTION_NOTIFICATION_REPLY = "de.intektor.kentai.notification.reply"

const val KEY_NOTIFICATION_ID = "de.intektor.kentai.notification.id"
const val KEY_NOTIFICATION_REPLY = "de.intektor.kentai.notification.reply.key"

const val KEY_NOTIFICATION_GROUP_ID = 0
const val KEY_NOTIFICATION_INITIALZE_CHAT_ID = 1_000_000

//Constants related to SendMediaActivity
const val KEY_CONTACT = "de.intektor.kentai.contact.key"
const val KEY_MEDIA_TYPE = "de.intektor.kentai.media_type.key"
const val KEY_CHAT_INFO = "de.intektor.kentai.chat_info.key"
const val KEY_MEDIA_URL = "de.intektor.kentai.media.url.key"
const val KEY_MESSAGE_TEXT = "de.intektor.kentai.message.text.key"

const val KEY_USER_UUID = "de.intektor.kentai.user_uuid.key"
const val KEY_FILE_URI = "de.intektor.kentai.file_uri.key"
const val KEY_MESSAGE_UUID = "de.intektor.kentai.message_uuid.key"
const val KEY_REFERENCE_UUID = "de.intektor.kentai.reference_uuid.key"

const val KEY_MEDIA_DATA = "de.intektor.kentai.media_data.key"

const val ACTION_USER_VIEW_CHAT = "de.intektor.kentai.user_view_chat.action"
const val KEY_CHAT_UUID = "de.intektor.kentai.chat_uuid.key"
const val KEY_CHAT_NAME = "de.intektor.kentai.chat_name.key"
const val KEY_CHAT_PARTICIPANTS = "de.intektor.kentai.chat_participants.key"
const val KEY_CHAT_TYPE = "de.intektor.kentai.chat_type.key"
const val KEY_USER_VIEW = "de.intektor.kentai.user_view_chat.user_view.key"

const val ACTION_DIRECT_CONNECTION_CONNECTED = "de.intektor.kentai.direct_connection_reconnected.action"

const val KEY_FOLDER = "de.intektor.kentai.folder.key"

const val ACTION_UPLOAD_PROFILE_PICTURE = "de.intektor.kentai.upload_profile_picture.action"
const val ACTION_CANCEL_UPLOAD_PROFILE_PICTURE = "de.intektor.kentai.upload_profile_picture.cancel.action"
const val KEY_PICTURE = "de.intektor.kentai.picture.key"

const val ACTION_INITIALIZE_CHAT = "de.intektor.kentai.initialize_chat.action"

const val NOTIFICATION_CHANNEL_NEW_MESSAGES = "de.intektor.kentai.notifications.new_messages.channel"
const val NOTIFICATION_CHANNEL_UPLOAD_PROFILE_PICTURE = "de.intektor.kentai.notifications.upload_profile_picture.channel"
const val NOTIFICATION_CHANNEL_UPLOAD_MEDIA = "de.intektor.kentai.notifications.upload_media.channel"
const val NOTIFICATION_CHANNEL_DOWNLOAD_MEDIA = "de.intektor.kentai.notification.download_media.channel"
const val NOTIFICATION_CHANNEL_MISC = "de.intektor.kentai.notification.misc.channel"

const val NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE = 1
const val NOTIFICATION_ID_DELETING_CHAT_MESSAGES = 990000
const val SP_DELETING_CHAT_MESSAGES = "de.intektor.kentai.notifications.deleting_chat_messages.sp"

const val ACTION_DOWNLOAD_PROFILE_PICTURE = "de.intektor.kentai.download_profile_picture.action"
const val KEY_PROFILE_PICTURE_TYPE = "de.intektor.kentai.download_profile_picture.type.key"

const val ACTION_PROFILE_PICTURE_UPDATED = "de.intektor.kentai.profile_picture_updated.action"
const val ACTION_PROFILE_PICTURE_UPLOADED = "de.intektor.kentai.profile_picture_uploaded.action"

const val ACTION_DOWNLOAD_NINE_GAG = "de.intektor.kentai.download_nine_gag.action"
const val KEY_NINE_GAG_ID = "de.intektor.kentai.download_nine_gag.gag_id.key"
const val KEY_NINE_GAG_UUID = "de.intektor.kentai.download_nine_gag.gag_uuid.key"

const val ACTION_CANCEL_DOWNLOAD_MEDIA = "de.intektor.kentai.cancel_download_media.action"

const val KEY_PROGRESS = "de.intektor.kentai.progress.key"
const val KEY_SUCCESSFUL = "de.intektor.kentai.successful.key"

const val ACTION_UPLOAD_REFERENCE = "de.intektor.kentai.upload_reference.action"

const val SHARED_PREFERENCES_THEME = "de.intektor.kentai.theme.sp"
const val SP_IS_LIGHT_THEME_KEY = "de.intektor.kentai.theme.key.sp"

const val SHARED_PREFERENCES_NOTIFICATIONS = "de.intektor.kentai.notifications.sp"
const val SP_NOTIFICATIONS_KEY = "de.intektor.kentai.notifications.key.sp"

const val KENTAI_NEW_MESSAGES_GROUP_KEY = "de.intektor.kentai.new_notifications.group_key.key"

const val ACTION_INIT_CHAT_FINISHED = "de.intektor.kentai.chat_initialization_finished.action"

const val ACTION_SEND_MESSAGES = "de.intektor.kentai.send_messages.action"
const val KEY_AMOUNT = "de.intektor.kentai.amount.key"
const val KEY_RECEIVER = "de.intektor.kentai.receiver.key"

const val ACTION_GROUP_MODIFICATION_RECEIVED = "de.intektor.kentai.group_modification_received.action"
const val KEY_GROUP_MODIFICATION_UUID = "de.intektor.kentai.modification_uuid.action"
const val KEY_GROUP_MODIFICATION = "de.intektor.kentai.group_modification.action"
const val KEY_GROUP_MODIFICATION_TYPE_ID = "de.intektor.kentai.group_modification_type_id.key"

const val ACTION_MESSAGE_STATUS_CHANGE = "de.intektor.kentai.message_status_change.action"
const val KEY_MESSAGE_STATUS = "de.intektor.kentai.message_status.key"
const val KEY_TIME = "de.intektor.kentai.time.key"

const val ACTION_CHAT_NOTIFICATION = "de.intektor.kentai.chat_notification.action"
const val KEY_UNREAD_MESSAGES = "de.intektor.kentai.unread_messages.key"
const val KEY_ADDITIONAL_INFO_REGISTRY_ID = "de.intektor.kentai.additional_info_registry_id.key"
const val KEY_ADDITIONAL_INFO_CONTENT = "de.intektor.kentai.additional_info_content.key"
const val KEY_MESSAGE_REGISTRY_ID = "de.intektor.kentai.message_registry_id.key"

const val SP_CAMERA_SETTINGS = "de.intektor.kentai.camera_settings.sp"
const val KEY_CAMERA_SETTINGS_FLASH_MODE = "de.intektor.kentai.camera_settings.flash_mode.key"
const val CAMERA_SETTINGS_FLASH_OFF = 0
const val CAMERA_SETTINGS_FLASH_AUTO = 1
const val CAMERA_SETTINGS_FLASH_ON = 2