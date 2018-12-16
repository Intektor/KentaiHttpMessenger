package de.intektor.mercury.ui.register

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.squareup.picasso.Picasso
import com.theartofdev.edmodo.cropper.CropImage
import de.intektor.mercury.R
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.ui.overview_activity.OverviewActivity
import kotlinx.android.synthetic.main.fragment_register_setup_profile.*

/**
 * @author Intektor
 */
class FragmentRegisterSetupProfile : Fragment() {

    companion object {
        private const val REQUEST_CODE_PICK_PROFILE_PICTURE = 0
    }

    private var profilePicture: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            layoutInflater.inflate(R.layout.fragment_register_setup_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Enable it because no profile picture is selected yet
        fragment_register_setup_profile_cl_remove_pp.isEnabled = false

        fragment_register_setup_profile_cl_add_pp.setOnClickListener {
            val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickPhoto, REQUEST_CODE_PICK_PROFILE_PICTURE)
        }

        fragment_register_setup_profile_cl_remove_pp.setOnClickListener {
            fragment_register_setup_profile_tv_add_pp.setText(R.string.register_add_profile_picture)

            fragment_register_setup_profile_iv_pp.setImageResource(R.drawable.baseline_account_circle_24)
        }

        fragment_register_setup_account_cl_done.setOnClickListener {
            val context = requireContext()
            val pp = profilePicture
            if (pp != null) {
                ChatMessageService.ActionUploadProfilePicture.launch(context, pp)
            }

            if (context is RegisterActivity) {
                context.finish()

                val i = Intent(context, OverviewActivity::class.java)
                startActivity(i)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_PICK_PROFILE_PICTURE -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    CropImage.activity(data.data)
                            .setAllowRotation(true)
                            .setAspectRatio(1, 1)
                            .start(requireContext(), this)
                }
            }
            CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    val result = CropImage.getActivityResult(data)

                    profilePicture = result.uri

                    Picasso.get().load(result.uri).placeholder(R.drawable.baseline_account_circle_24).into(fragment_register_setup_profile_iv_pp)

                    fragment_register_setup_profile_tv_add_pp.setText(R.string.register_edit_profile_picture)
                    fragment_register_setup_profile_cl_remove_pp.isEnabled = true
                }
            }
        }
    }
}