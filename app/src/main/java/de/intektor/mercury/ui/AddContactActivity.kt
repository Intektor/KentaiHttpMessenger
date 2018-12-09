package de.intektor.mercury.ui

import android.os.AsyncTask
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury.util.getCompatDrawable
import de.intektor.mercury_common.client_to_server.AddContactRequest
import de.intektor.mercury_common.client_to_server.QueryUsersRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.AddContactResponse
import de.intektor.mercury_common.server_to_client.QueryUsersResponse
import de.intektor.mercury_common.users.ProfilePictureType
import kotlinx.android.synthetic.main.activity_add_contact.*
import java.security.interfaces.RSAPublicKey
import java.util.*

class AddContactActivity : AppCompatActivity() {

    private val currentQuery = mutableListOf<QueriedUserWrapper>()

    private lateinit var adapter: QueriedUserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_add_contact)

        activity_add_contact_rv_contacts.layoutManager = LinearLayoutManager(this)
        adapter = QueriedUserAdapter(currentQuery) { position ->
            val item = currentQuery[position]
            val user = item.queriedUser.user

            ContactUtil.addContact(user.userUUID, user.username, mercuryClient().dataBase, user.messageKey)

            item.added = true

            adapter.notifyItemChanged(position)
        }

        activity_add_contact_rv_contacts.adapter = adapter

        activity_add_contact_pb_loading.visibility = View.GONE

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_add_contact, menu)

        val searchItem = menu.findItem(R.id.menu_add_contact_search)
        searchItem.expandActionView()

        val searchView = searchItem.actionView as SearchView
        searchView.onActionViewExpanded()

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                queryUsers(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                queryUsers(newText ?: "")
                return true
            }
        })

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean = false

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                finish()
                return true
            }

        })

        return true
    }

    private fun queryUsers(query: String) {
        if (query.isBlank()) {
            displayMessage(R.string.activity_add_contact_tv_query_users)
            return
        }

        HttpManager.asyncCall(genGson().toJson(QueryUsersRequest(query)), QueryUsersRequest.TARGET) { response ->
            if (response == null || response.isBlank()) {
                displayMessage(R.string.activity_add_contact_tv_error)

                return@asyncCall
            }

            val queryResponse = genGson().fromJson(response, QueryUsersResponse::class.java)

            if (queryResponse.users.isEmpty()) {
                displayMessage(R.string.activity_add_contact_tv_no_user_found)

                return@asyncCall
            }

            activity_add_contact_pb_loading.visibility = View.GONE
            activity_add_contact_tv_info.visibility = View.GONE
            activity_add_contact_rv_contacts.visibility = View.VISIBLE

            currentQuery.clear()
            currentQuery += queryResponse.users.map { queriedUser ->
                QueriedUserWrapper(queriedUser, ContactUtil.hasContact(mercuryClient().dataBase, queriedUser.user.userUUID))
            }

            adapter.notifyDataSetChanged()
        }
    }

    private fun displayMessage(message: Int) {
        activity_add_contact_pb_loading.visibility = View.GONE
        activity_add_contact_tv_info.visibility = View.VISIBLE
        activity_add_contact_tv_info.text = getString(message)
        activity_add_contact_rv_contacts.visibility = View.GONE
    }

    private fun addNewContact(key: RSAPublicKey, userUUID: UUID) {
//        ContactUtil.addContact(userUUID, add_contact_username_field.text.toString(), mercuryClient.dataBase, key)
    }

    private class AddContactTask(val callback: (AddContactResponse?) -> (Unit), val username: String) : AsyncTask<Unit, Unit, AddContactResponse?>() {
        override fun doInBackground(vararg p0: Unit?): AddContactResponse? {
            return try {
                val gson = genGson()

                val res = HttpManager.post(gson.toJson(AddContactRequest(username)), AddContactRequest.TARGET)

                gson.fromJson(res, AddContactResponse::class.java)
            } catch (t: Throwable) {
                null
            }
        }

        override fun onPostExecute(result: AddContactResponse?) {
            callback.invoke(result)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        finish()
    }

    private class QueriedUserAdapter(val items: List<QueriedUserWrapper>, private val onAddClicked: (position: Int) -> Unit) : RecyclerView.Adapter<QueriedUserAdapter.QueriedUserViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueriedUserViewHolder =
                QueriedUserViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_queried_user, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: QueriedUserViewHolder, position: Int) {
            val item = items[position]
            val user = item.queriedUser.user
            holder.username.text = user.username

            holder.addParentCv.visibility = if (item.added) View.GONE else View.VISIBLE
            holder.addedParentCv.visibility = if (item.added) View.VISIBLE else View.GONE

            Picasso.get().cancelRequest(holder.profilePicture)

            val context = holder.itemView.context
            ProfilePictureUtil.loadProfilePicture(user.userUUID, ProfilePictureType.SMALL, holder.profilePicture,
                    context.resources.getCompatDrawable(R.drawable.baseline_account_circle_24, context.theme))

            holder.addParentCl.setOnClickListener {
                onAddClicked(position)
            }
        }

        private class QueriedUserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val username: TextView = view.findViewById(R.id.item_queried_user_tv_username)
            val joined: TextView = view.findViewById(R.id.item_queried_user_tv_joined)
            val addParentCv: CardView = view.findViewById(R.id.item_queried_user_cv_add_parent)
            val addParentCl: ConstraintLayout = view.findViewById(R.id.item_queried_user_cl_add_parent)
            val addedParentCv: CardView = view.findViewById(R.id.item_queried_user_cv_added_parent)
            val profilePicture: ImageView = view.findViewById(R.id.item_queried_user_iv_profile_picture)
        }
    }

    private data class QueriedUserWrapper(val queriedUser: QueryUsersResponse.QueriedUser, var added: Boolean)
}
