/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media;

import static androidx.media.MediaMetadata2.METADATA_KEY_DISPLAY_DESCRIPTION;
import static androidx.media.MediaMetadata2.METADATA_KEY_DISPLAY_ICON;
import static androidx.media.MediaMetadata2.METADATA_KEY_DISPLAY_ICON_URI;
import static androidx.media.MediaMetadata2.METADATA_KEY_DISPLAY_SUBTITLE;
import static androidx.media.MediaMetadata2.METADATA_KEY_DISPLAY_TITLE;
import static androidx.media.MediaMetadata2.METADATA_KEY_EXTRAS;
import static androidx.media.MediaMetadata2.METADATA_KEY_MEDIA_ID;
import static androidx.media.MediaMetadata2.METADATA_KEY_MEDIA_URI;
import static androidx.media.MediaMetadata2.METADATA_KEY_TITLE;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public class MediaUtils2 {
    /**
     * Creates a {@link MediaItem} from the {@link MediaItem2}.
     *
     * @param item2 an item.
     * @return The newly created media item.
     */
    public static MediaItem createMediaItem(MediaItem2 item2) {
        if (item2 == null) {
            return null;
        }
        MediaDescriptionCompat descCompat;

        MediaMetadata2 metadata = item2.getMetadata();
        if (metadata == null) {
            descCompat = new MediaDescriptionCompat.Builder()
                    .setMediaId(item2.getMediaId())
                    .build();
        } else {
            MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                    .setMediaId(item2.getMediaId())
                    .setSubtitle(metadata.getText(METADATA_KEY_DISPLAY_SUBTITLE))
                    .setDescription(metadata.getText(METADATA_KEY_DISPLAY_DESCRIPTION))
                    .setIconBitmap(metadata.getBitmap(METADATA_KEY_DISPLAY_ICON))
                    .setExtras(metadata.getExtras());

            String title = metadata.getString(METADATA_KEY_TITLE);
            if (title != null) {
                builder.setTitle(title);
            } else {
                builder.setTitle(metadata.getString(METADATA_KEY_DISPLAY_TITLE));
            }

            String displayIconUri = metadata.getString(METADATA_KEY_DISPLAY_ICON_URI);
            if (displayIconUri != null) {
                builder.setIconUri(Uri.parse(displayIconUri));
            }

            String mediaUri = metadata.getString(METADATA_KEY_MEDIA_URI);
            if (mediaUri != null) {
                builder.setMediaUri(Uri.parse(mediaUri));
            }

            descCompat = builder.build();
        }
        return new MediaItem(descCompat, item2.getFlags());
    }

    /**
     * Creates a {@link MediaItem2} from the {@link MediaItem}.
     *
     * @param item an item.
     * @return The newly created media item.
     */
    public static MediaItem2 createMediaItem2(MediaItem item) {
        if (item == null || item.getMediaId() == null) {
            return null;
        }

        MediaMetadata2 metadata2 = createMediaMetadata2(item.getDescription());
        return new MediaItem2.Builder(item.getFlags())
                .setMediaId(item.getMediaId())
                .setMetadata(metadata2)
                .build();
    }

    /**
     * Creates a {@link MediaMetadata2} from the {@link MediaDescriptionCompat}.
     *
     * @param descCompat A {@link MediaDescriptionCompat} object.
     * @return The newly created {@link MediaMetadata2} object.
     */
    public static MediaMetadata2 createMediaMetadata2(MediaDescriptionCompat descCompat) {
        if (descCompat == null) {
            return null;
        }

        MediaMetadata2.Builder metadata2Builder = new MediaMetadata2.Builder();
        metadata2Builder.putString(METADATA_KEY_MEDIA_ID, descCompat.getMediaId());

        CharSequence title = descCompat.getTitle();
        if (title != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_TITLE, title);
        }

        CharSequence description = descCompat.getDescription();
        if (description != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_DESCRIPTION, descCompat.getDescription());
        }

        CharSequence subtitle = descCompat.getSubtitle();
        if (subtitle != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_SUBTITLE, subtitle);
        }

        Bitmap icon = descCompat.getIconBitmap();
        if (icon != null) {
            metadata2Builder.putBitmap(METADATA_KEY_DISPLAY_ICON, icon);
        }

        Uri iconUri = descCompat.getIconUri();
        if (iconUri != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_ICON_URI, iconUri.toString());
        }

        Bundle bundle = descCompat.getExtras();
        if (bundle != null) {
            metadata2Builder.setExtras(descCompat.getExtras());
        }

        Uri mediaUri = descCompat.getMediaUri();
        if (mediaUri != null) {
            metadata2Builder.putText(METADATA_KEY_MEDIA_URI, mediaUri.toString());
        }

        return metadata2Builder.build();
    }

    /**
     * Creates a {@link MediaMetadata2} from the {@link MediaMetadataCompat}.
     *
     * @param metadataCompat A {@link MediaMetadataCompat} object.
     * @return The newly created {@link MediaMetadata2} object.
     */
    public MediaMetadata2 createMediaMetadata2(MediaMetadataCompat metadataCompat) {
        if (metadataCompat == null) {
            return null;
        }
        return new MediaMetadata2(metadataCompat.getBundle());
    }

    /**
     * Creates a {@link MediaMetadataCompat} from the {@link MediaMetadata2}.
     *
     * @param metadata2 A {@link MediaMetadata2} object.
     * @return The newly created {@link MediaMetadataCompat} object.
     */
    public MediaMetadataCompat createMediaMetadataCompat(MediaMetadata2 metadata2) {
        if (metadata2 == null) {
            return null;
        }

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();

        List<String> skippedKeys = new ArrayList<>();
        Bundle bundle = metadata2.toBundle();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof CharSequence) {
                builder.putText(key, (CharSequence) value);
            } else if (value instanceof Rating2) {
                builder.putRating(key, createRatingCompat((Rating2) value));
            } else if (value instanceof Bitmap) {
                builder.putBitmap(key, (Bitmap) value);
            } else if (value instanceof Long) {
                builder.putLong(key, (Long) value);
            } else {
                // There is no 'float' or 'bundle' type in MediaMetadataCompat.
                skippedKeys.add(key);
            }
        }

        MediaMetadataCompat result = builder.build();
        for (String key : skippedKeys) {
            Object value = bundle.get(key);
            if (value instanceof Float) {
                // Compatibility for MediaMetadata2.Builder.putFloat()
                result.getBundle().putFloat(key, (Float) value);
            } else if (METADATA_KEY_EXTRAS.equals(value)) {
                // Compatibility for MediaMetadata2.Builder.setExtras()
                result.getBundle().putBundle(key, (Bundle) value);
            }
        }
        return result;
    }

    /**
     * Creates a {@link Rating2} from the {@link RatingCompat}.
     *
     * @param ratingCompat A {@link RatingCompat} object.
     * @return The newly created {@link Rating2} object.
     */
    public Rating2 createRating2(RatingCompat ratingCompat) {
        if (ratingCompat == null) {
            return null;
        }
        if (!ratingCompat.isRated()) {
            return Rating2.newUnratedRating(ratingCompat.getRatingStyle());
        }

        switch (ratingCompat.getRatingStyle()) {
            case RatingCompat.RATING_3_STARS:
            case RatingCompat.RATING_4_STARS:
            case RatingCompat.RATING_5_STARS:
                return Rating2.newStarRating(
                        ratingCompat.getRatingStyle(), ratingCompat.getStarRating());
            case RatingCompat.RATING_HEART:
                return Rating2.newHeartRating(ratingCompat.hasHeart());
            case RatingCompat.RATING_THUMB_UP_DOWN:
                return Rating2.newThumbRating(ratingCompat.isThumbUp());
            case RatingCompat.RATING_PERCENTAGE:
                return Rating2.newPercentageRating(ratingCompat.getPercentRating());
            default:
                return null;
        }
    }

    /**
     * Creates a {@link RatingCompat} from the {@link Rating2}.
     *
     * @param rating2 A {@link Rating2} object.
     * @return The newly created {@link RatingCompat} object.
     */
    public RatingCompat createRatingCompat(Rating2 rating2) {
        if (rating2 == null) {
            return null;
        }
        if (!rating2.isRated()) {
            return RatingCompat.newUnratedRating(rating2.getRatingStyle());
        }

        switch (rating2.getRatingStyle()) {
            case Rating2.RATING_3_STARS:
            case Rating2.RATING_4_STARS:
            case Rating2.RATING_5_STARS:
                return RatingCompat.newStarRating(
                        rating2.getRatingStyle(), rating2.getStarRating());
            case Rating2.RATING_HEART:
                return RatingCompat.newHeartRating(rating2.hasHeart());
            case Rating2.RATING_THUMB_UP_DOWN:
                return RatingCompat.newThumbRating(rating2.isThumbUp());
            case Rating2.RATING_PERCENTAGE:
                return RatingCompat.newPercentageRating(rating2.getPercentRating());
            default:
                return null;
        }
    }
}
