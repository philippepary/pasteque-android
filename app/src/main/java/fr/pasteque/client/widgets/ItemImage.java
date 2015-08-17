package fr.pasteque.client.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.widget.ImageView;
import fr.pasteque.client.R;
import fr.pasteque.client.data.ImagesData;
import fr.pasteque.client.models.Product;
import fr.pasteque.client.models.interfaces.Item;

/**
 * Created by nsvir on 17/08/15.
 * n.svirchevsky@gmail.com
 */
public class ItemImage extends ImageView {

    private final static int default_img = R.drawable.ic_placeholder_img;

    public ItemImage(Context context) {
        super(context);
    }

    public ItemImage(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ItemImage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ItemImage(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setItem(Item item) {

        if (item.hasImage()) {
            this.setVisibility(INVISIBLE);
            switch (item.getType()) {
                case Category:
                    new CategoryImageAsyncTask().execute(item.getId());
                    break;
                case Product:
                    new ProductImageAsyncTask().execute(item.getId());
                    break;
            }
        } else {
            this.setImageResource(ItemImage.default_img);
        }
    }

    private abstract class ImageAsyncTask extends AsyncTask<String, Long, Bitmap> {
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null) {
                ItemImage.this.setImageBitmap(bitmap);
            } else {
                ItemImage.this.setImageResource(ItemImage.default_img);
            }
            ItemImage.this.setVisibility(VISIBLE);
        }
    }

    private class ProductImageAsyncTask extends ImageAsyncTask {

        @Override
        protected Bitmap doInBackground(String... ids) {
            return ImagesData.getProductImage(getContext(), ids[0]);
        }
    }

    private class CategoryImageAsyncTask extends ImageAsyncTask {

        @Override
        protected Bitmap doInBackground(String... ids) {
            return ImagesData.getCategoryImage(getContext(), ids[0]);
        }
    }
}
