package br.com.harkin.barcodeplus;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

import br.com.harkin.barcodeplus.adapter.ItemAdapter;
import br.com.harkin.barcodeplus.listener.ModuleInstallProgressListener;
import br.com.harkin.barcodeplus.model.Item;

public class MainActivity extends AppCompatActivity {
    public ItemAdapter adapter;
    List<Item> itemList = new ArrayList<>();

    public GmsBarcodeScanner scanner;
    public ModuleInstallClient moduleInstallClient;
    public ExtendedFloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        itemList = obtainItems();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = findViewById(R.id.fab);

        ListView listView = findViewById(R.id.listView);

        adapter = new ItemAdapter(this, itemList);
        listView.setAdapter(adapter);

        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .allowManualInput()
                .enableAutoZoom()
                .setBarcodeFormats(
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E)
                .build();

        scanner = GmsBarcodeScanning.getClient(this, options);

        moduleInstallClient = ModuleInstall.getClient(this);

        moduleInstallClient
                .areModulesAvailable(scanner)
                .addOnSuccessListener(
                        response -> {
                            if (!response.areModulesAvailable()) {
                                Snackbar.make(fab, "Bibliotecas não instaladas, iniciando instalação...", Snackbar.LENGTH_LONG)
                                        .setAnchorView(R.id.fab)
                                        .setAction("Action", null).show();

                                dispatchGoogleLibDownload();
                            }
                        })
                .addOnFailureListener(
                        e -> {
                            Snackbar.make(fab, "Não foi possível prosseguir com a instalação das bibliotecas.", Snackbar.LENGTH_LONG)
                                    .setAnchorView(R.id.fab)
                                    .setAction("Action", null).show();
                        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanner
                        .startScan()
                        .addOnSuccessListener(
                                barcode -> {
                                    String rawValue = barcode.getRawValue();

                                    displayBarcodePopup(view, rawValue);
                                })
                        .addOnCanceledListener(
                                () -> {
                                    // Task canceled
                                })
                        .addOnFailureListener(
                                e -> {
                                    // Task failed with an exception
                                });
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ItemAdapter adapter = (ItemAdapter) listView.getAdapter();

                Item clickedItem = adapter.getItem(position);

                String name = clickedItem.getName();

                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, name);
                sendIntent.setType("text/plain");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            }
        });
    }

    public void displayBarcodePopup(View view, String barcodeResult) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Deseja adicionar o código escaneado?")
                .setMessage(barcodeResult)
                .setNeutralButton("Cancelar", (dialog, which) -> {
                    // Resposta ao botão neutro
                })
                .setPositiveButton("Adicionar", (dialog, which) -> {
                    addScanToList(view, barcodeResult);
                })
                .show();
    }

    public void addScanToList(View view, String barcodeResult) {
        try {
            Item newItem = new Item(barcodeResult);
            itemList.add(0, newItem);
            adapter.notifyDataSetChanged();

            Gson gson = new Gson();
            String json = gson.toJson(itemList);

            SharedPreferences sharedPreferences = getSharedPreferences("BARCODE", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("barcodes", json);
            editor.apply();

            Snackbar.make(view, "Código de barras adicionado com sucesso.", Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.fab)
                    .setAction("Action", null).show();
        } catch (Exception e) {
            Snackbar.make(view, "Houve um erro ao adicionar o código de barras.", Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.fab)
                    .setAction("Action", null).show();
        }
    }

    private List<Item> obtainItems() {
        SharedPreferences sharedPreferences = getSharedPreferences("BARCODE", Context.MODE_PRIVATE);
        String json = sharedPreferences.getString("barcodes", "");

        if (!json.isEmpty()) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Item>>() {}.getType();
            return gson.fromJson(json, type);
        }

        return new ArrayList<>();
    }

    public void deleteList() {
        SharedPreferences sharedPreferences = getSharedPreferences("BARCODE", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().apply();

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    public void displayAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sobre")
                .setMessage("Versão: 1.1\n" +
                        "\n" +
                        "Desenvolvido por Darkton Hallisson (@soudarkton).\n" +
                        "Copyright © 2023.\n" +
                        "\n" +
                        "Este software utiliza a licença do MIT.\n" +
                        "\n" +
                        "O software faz uso das seguintes bibliotecas:\n" +
                        "\n" +
                        "- AndroidX AppCompat: 1.6.1\n" +
                        "- Material Components for Android: 1.10.0\n" +
                        "- ConstraintLayout: 2.1.4\n" +
                        "- AndroidX Navigation Fragment: 2.5.3\n" +
                        "- AndroidX Navigation UI: 2.5.3\n" +
                        "- JUnit: 4.13.2\n" +
                        "- AndroidX Test JUnit: 1.1.5\n" +
                        "- Espresso Core: 3.5.1\n" +
                        "- Google Play Services Code Scanner: 16.1.0\n" +
                        "- Google Gson: 2.10.1\n" +
                        "- Google Play Services Base: 18.2.0")
                .setPositiveButton("Fechar", (dialog, which) -> {
                    // Resposta ao botão positivo
                })
                .show();
    }

    public void dispatchGoogleLibDownload() {
        View customDialogView = LayoutInflater.from(this).inflate(R.layout.dialog_layout, null);

        ProgressBar progressBar = customDialogView.findViewById(R.id.progressBar);

        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Instalando bibliotecas necessárias...")
                .setView(customDialogView)
                .setCancelable(false)
                .show();

        ModuleInstallProgressListener progressListener = new ModuleInstallProgressListener(alertDialog, moduleInstallClient, progressBar);

        ModuleInstallRequest moduleInstallRequest =
                ModuleInstallRequest.newBuilder()
                        .addApi(scanner)
                        .setListener(progressListener)
                        .build();

        moduleInstallClient.installModules(moduleInstallRequest)
                .addOnSuccessListener(response -> {
                    if (response.areModulesAlreadyInstalled()) {
                        Snackbar.make(fab, "Bibliotecas instaladas com sucesso.", Snackbar.LENGTH_LONG)
                                .setAnchorView(R.id.fab)
                                .setAction("Action", null).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(fab, "Não foi possível prosseguir com a instalação das bibliotecas.", Snackbar.LENGTH_LONG)
                            .setAnchorView(R.id.fab)
                            .setAction("Action", null).show();
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.delete_all) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Deseja deletar todos os códigos?")
                    .setMessage("Cuidado! Esta ação não poderá ser revertida.")
                    .setNeutralButton("Cancelar", (dialog, which) -> {
                        // Resposta ao botão neutro
                    })
                    .setPositiveButton("Deletar", (dialog, which) -> {
                        deleteList();
                    })
                    .show();
        }

        if (item.getItemId() == R.id.about) {
            displayAbout();
        }

        return super.onOptionsItemSelected(item);
    }
}