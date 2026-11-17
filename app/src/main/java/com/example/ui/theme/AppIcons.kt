package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.R

/**
 * Lightweight local VectorDrawable icon bridge replacing `material-icons-extended` (~10 MB)
 * while keeping the exact Material Design icon geometry and auto-mirroring behavior.
 * Standard icons already present in `material-icons-core` continue to use `Icons.Default` /
 * `Icons.AutoMirrored.Filled`.
 */
object AppIcons {
    val Launch: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_launch)
    val Undo: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_undo)

    val AddPhotoAlternate: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_add_photo_alternate)
    val AdminPanelSettings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_admin_panel_settings)
    val Archive: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_archive)
    val AttachFile: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_attach_file)
    val ChatBubble: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_chat_bubble)
    val ChatBubbleOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_chat_bubble_outline)
    val CleaningServices: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_cleaning_services)
    val Cloud: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_cloud)
    val CloudDownload: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_cloud_download)
    val CloudQueue: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_cloud_queue)
    val CloudUpload: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_cloud_upload)
    val Compress: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_compress)
    val Contacts: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_contacts)
    val ContentCopy: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_content_copy)
    val Dashboard: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_dashboard)
    val DeleteForever: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_delete_forever)
    val DeleteSweep: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_delete_sweep)
    val Download: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_download)
    val ExpandLess: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_expand_less)
    val ExpandMore: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_expand_more)
    val FilterListOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_filter_list_off)
    val Forum: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_forum)
    val Groups: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_groups)
    val Image: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_image)
    val Inbox: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_inbox)
    val InstallMobile: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_install_mobile)
    val LocalOffer: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_local_offer)
    val LockOpen: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_lock_open)
    val MarkChatRead: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_mark_chat_read)
    val OpenInFull: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_open_in_full)
    val Password: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_password)
    val PersonSearch: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_person_search)
    val Report: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_report)
    val Restore: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_restore)
    val RestoreFromTrash: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_restore_from_trash)
    val Save: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_save)
    val Science: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_science)
    val SearchOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_search_off)
    val Security: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_security)
    val Settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_settings)
    val Shield: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_shield)
    val Smartphone: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_smartphone)
    val Sms: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_sms)
    val SystemUpdate: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_system_update)
    val Tune: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_tune)
    val Unarchive: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_unarchive)
    val Verified: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_verified)
    val VerifiedUser: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_verified_user)
    val ViewAgenda: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_view_agenda)
    val VpnKey: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_m_vpn_key)
}
