/*
    POS-Tech Android
    Copyright (C) 2012 SARL SCOP Scil (contact@scil.coop)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package fr.postech.client;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Message;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fr.postech.client.models.Catalog;
import fr.postech.client.models.Category;
import fr.postech.client.models.User;
import fr.postech.client.models.Product;
import fr.postech.client.utils.HostParser;
import fr.postech.client.utils.URLTextGetter;

public class Sync {

    public static final int SYNC_DONE = 1;
    public static final int CONNECTION_FAILED = 2;
    public static final int CATALOG_SYNC_DONE = 3;
    public static final int USERS_SYNC_DONE = 4;
    public static final int CATEGORIES_SYNC_DONE = 5;

    private Context ctx;
    private Handler listener;
    private boolean categoriesDone;
    private boolean productsDone;
    private boolean usersDone;

    private ProgressDialog progress;
    /** The catalog to build with multiple syncs */
    private Catalog catalog;
    /** Categories by id for quick products assignment */
    private Map<String, Category> categories;

    public Sync(Context ctx, Handler listener) {
        this.listener = listener;
        this.ctx = ctx;
        this.catalog = new Catalog();
        this.categories = new HashMap<String, Category>();
    }

    /** Launch synchronization and display progress dialog */
    public void startSync() {
        this.progress = new ProgressDialog(ctx);
        this.progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        this.progress.setMax(3);
        this.progress.setTitle(ctx.getString(R.string.sync_title));
        this.progress.setMessage(ctx.getString(R.string.sync_message));
        this.progress.show();
        synchronize();
    }

    public void synchronize() {
        String baseUrl = HostParser.getHostFromPrefs(this.ctx);
        String categoriesUrl = baseUrl + "api/CategoriesAPI?action=getAll";
        String productsUrl = baseUrl + "api/ProductsAPI?action=getAllFull";
        String usersUrl = baseUrl + "api/UsersAPI?action=getAll";
        URLTextGetter.getText(categoriesUrl,
                              new DataHandler(DataHandler.TYPE_CATEGORY));
        URLTextGetter.getText(usersUrl, new DataHandler(DataHandler.TYPE_USER));
    }
    
    /** Parse categories and start products sync to create catalog */
    private void parseCategories(String json) {
        Map<String, List<Category>> children = new HashMap<String, List<Category>>();
        try {
            JSONArray array = new JSONArray(json);
            // First pass: read all and register parents
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Category c = Category.fromJSON(o);
                String parent = null;
                if (!o.isNull("parent_id")) {
                    parent = o.getString("parent_id");
                }
                if (!children.containsKey(parent)) {
                    children.put(parent, new ArrayList<Category>());
                }
                children.get(parent).add(c);
                this.categories.put(c.getId(), c);
            }
            // Second pass: build subcategories
            for (Category root : children.get(null)) {
                // Build subcategories
                this.parseSubcats(root, children);
                // This branch is ready, add to catalog
                this.catalog.addRootCategory(root);
            }
        } catch(JSONException e) {
            e.printStackTrace();
            // TODO: shouldn't break parsing
        }
        if (listener != null) {
            Message m = listener.obtainMessage();
            m.what = CATEGORIES_SYNC_DONE;
            m.obj = children.get(null);
            m.sendToTarget();
        }
        // Start synchronizing products
        String baseUrl = HostParser.getHostFromPrefs(this.ctx);
        String productsUrl = baseUrl + "api/ProductsAPI?action=getAllFull";
        URLTextGetter.getText(productsUrl,
                              new DataHandler(DataHandler.TYPE_PRODUCT));
    }

    // recursive subroutine of parseCategories
    private void parseSubcats(Category c,
                              Map<String, List<Category>> children) {
        if (children.containsKey(c.getId())) {
            for (Category sub : children.get(c.getId())) {
                c.addSubcategory(sub);
                this.parseSubcats(sub, children);
            }
        }
    }

    private void parseProducts(String json) {
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Product p = Product.fromJSON(o);
                // Find its category and add it
                String catId = o.getJSONObject("category").getString("id");
                for (Category c : this.catalog.getRootCategories()) {
                    if (c.getId().equals(catId)) {
                        this.catalog.addProduct(c, p);
                        break;
                    }
                }
            }
        } catch(JSONException e) {
            e.printStackTrace();
            // TODO: shouldn't break parsing
        }
        if (listener != null) {
            Message m = listener.obtainMessage();
            m.what = CATALOG_SYNC_DONE;
            m.obj = this.catalog;
            m.sendToTarget();
        }
    }

    private void parseUsers(String json) {
        List<User> users = new ArrayList<User>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                User u = User.fromJSON(o);
                users.add(u);
            }
        } catch(JSONException e) {
            e.printStackTrace();
            // TODO: shouldn't break parsing
        }
        if (listener != null) {
            Message m = listener.obtainMessage();
            m.what = USERS_SYNC_DONE;
            m.obj = users;
            m.sendToTarget();
        }
    }
    
    private void checkFinished() {
        if (this.productsDone && this.usersDone) {
            if (this.progress != null) {
                this.progress.dismiss();
                this.progress = null;
            }
            Message m = this.listener.obtainMessage();
            m.what = SYNC_DONE;
            m.sendToTarget();
        }
    }

    private class DataHandler extends Handler {
        
        private static final int TYPE_USER = 1;
        private static final int TYPE_PRODUCT = 2;
        private static final int TYPE_CATEGORY = 3;

        private int type;
        
        public DataHandler(int type) {
            this.type = type;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (this.type) {
            case TYPE_USER:
                Sync.this.usersDone = true;
                break;
            case TYPE_PRODUCT:
                Sync.this.productsDone = true;
                break;
            case TYPE_CATEGORY:
                Sync.this.categoriesDone = true;
                break;
            }
            if (progress != null) {
                progress.incrementProgressBy(1);
            }
            switch (msg.what) {
            case URLTextGetter.SUCCESS:
                // Parse content
                String content = (String) msg.obj;
                switch (type) {
                case TYPE_USER:
                    parseUsers(content);
                    break;
                case TYPE_PRODUCT:
                    parseProducts(content);
                    break;
                case TYPE_CATEGORY:
                    parseCategories(content);
                    break;
                }
                break;
            case URLTextGetter.ERROR:
                ((Exception)msg.obj).printStackTrace();
            case URLTextGetter.STATUS_NOK:
                if (listener != null) {
                    Message m = listener.obtainMessage();
                    m.what = CONNECTION_FAILED;
                    m.obj = msg.obj;
                    m.sendToTarget();
                }
                break;
            }
            checkFinished();
        }
    }

}