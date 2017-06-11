package com.skbuf.datagenerator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CreateCustomSampleFragment extends Fragment {

    private final String TAG = "CreateCustomSample";

    private List<Uri> filesSelected = new ArrayList<Uri>();
    private final Integer FILE_SELECT_CODE = 1;
    private final Integer FILE_CREATE_CODE = 2;

    private Button buttonSave, buttonBrowse;
    private ListView lv;
    private FileListAdapter adapter;
    Switch switchOffset;
    EditText globalOffset;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_custom_sample, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        buttonSave = (Button) getView().findViewById(R.id.button_create_save);
        buttonBrowse = (Button) getView().findViewById(R.id.button_create_browse);

        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/*");
                Intent cintent = Intent.createChooser(intent, "Choose files");
                startActivityForResult(cintent, FILE_CREATE_CODE);
            }
        });


        buttonBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                String samplesDir = SamplingData.getSamplesPath();
                Uri uri = Uri.parse(samplesDir);
                intent.setDataAndType(uri, "text/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                Intent cintent = Intent.createChooser(intent, "Choose files");
                startActivityForResult(cintent, FILE_SELECT_CODE);

            }
        });

        adapter = new FileListAdapter(getContext(), filesSelected);
        lv = (ListView) getView().findViewById(R.id.list);
        lv.setAdapter(adapter);

        switchOffset = (Switch) getView().findViewById(R.id.swith_offset);
        globalOffset = (EditText) getView().findViewById(R.id.offset);
        switchOffset.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked == true) {
                    globalOffset.setEnabled(true);
                } else {
                    globalOffset.setEnabled(false);
                }
            }
        });
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                filesSelected.add(data.getData());
                adapter.notifyDataSetChanged();
            }
        } else if (requestCode == FILE_CREATE_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                String customSampleFile = uri.toString();

                createCustomSample(uri);
                Log.d(TAG, "Created custom file: " + customSampleFile);
            }
        }
    }

    public void createCustomSample(Uri uri) {
        Boolean globalOffsetEnabled = switchOffset.isChecked();
        Integer offsetValue = 0;
        if (globalOffsetEnabled == true) {
            offsetValue = Integer.parseInt(globalOffset.getText().toString());
        }
        String line;

        // concatenate files and adjust timestamp
        try {
            ParcelFileDescriptor outputPfd = getActivity().getContentResolver().openFileDescriptor(uri, "w");
            FileOutputStream fileOutputStream = new FileOutputStream(outputPfd.getFileDescriptor());

            for (Uri inputUri : filesSelected) {
                ParcelFileDescriptor inputPfd = getActivity().getContentResolver().openFileDescriptor(inputUri, "r");
                FileInputStream fileInputStream = new FileInputStream(inputPfd.getFileDescriptor());
                BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));

                if (globalOffsetEnabled == true) {
                    // get first timestamp and write in the output file
                    line = br.readLine();
                    String columns[] = line.split(" ");
                    Long startTimestamp = Long.parseLong(columns[0]);
                    String newLine = "0 " + columns[1] + " " + columns[2] + " " + columns[3] + "\n";
                    fileOutputStream.write(newLine.getBytes());

                    while ((line = br.readLine()) != null)   {
                        columns = line.split(" ");
                        Long timestamp = Long.parseLong(columns[0]) - startTimestamp + offsetValue;
                        newLine = timestamp + " " + columns[1] + " " + columns[2] + " " + columns[3] + "\n";
                        fileOutputStream.write(newLine.getBytes());
                    }
                } else {
                    while ((line = br.readLine()) != null)   {
                        line = line + "\n";
                        fileOutputStream.write(line.getBytes());
                    }
                }

                br.close();
                fileInputStream.close();
            }

            fileOutputStream.close();
            outputPfd.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
