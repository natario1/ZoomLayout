package com.otaliastudios.zoom.demo;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GridViewAdapter extends BaseAdapter {

    private List<String> items = new ArrayList<>();

    GridViewAdapter() {
        Random r = new Random();
        for (int i = 0; i < 300; i++) {
            items.add(String.valueOf(r.nextInt(10)));
        }
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public String getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Button buttonView;
        if (convertView == null) {
            buttonView = new Button(parent.getContext());
            buttonView.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            buttonView.setText(getItem(position));
            buttonView.setTextColor(Color.RED);
            buttonView.setBackgroundColor(Color.WHITE);
        } else {
            buttonView = (Button) convertView;
        }

        return buttonView;
    }
}
