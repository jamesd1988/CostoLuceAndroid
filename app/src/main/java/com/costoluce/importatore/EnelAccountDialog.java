package com.costoluce.importatore;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/** Native form: account secrets never enter the WebView or its local storage. */
final class EnelAccountDialog extends Dialog {
    interface Connect { void run(EnelConnector.Credentials credentials, boolean remember); }
    private static final int BG=0xff06182d, PANEL=0xff102b49, FIELD=0xff081f37;
    private static final int TEXT=0xffedf5ff, MUTED=0xffacbfd4, BORDER=0xff294968, TEAL=0xff65e3cd;
    private final EnelConnector.Credentials initial;
    private final String restoreWarning;
    private final Connect connect;
    private final Runnable tools;
    private final boolean saved;
    private EditText user,password,pod,number,fiscal;
    private CheckBox remember;
    private TextView error;

    EnelAccountDialog(Activity activity, EnelConnector.Credentials initial, boolean saved,
                      String restoreWarning, Connect connect, Runnable tools) {
        super(activity,android.R.style.Theme_Material_NoActionBar);
        this.initial=initial;this.saved=saved;this.restoreWarning=restoreWarning;
        this.connect=connect;this.tools=tools;
    }
    private int dp(float value){return Math.round(value*getContext().getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));
        if(color!=BG)drawable.setStroke(dp(1),BORDER);return drawable;
    }
    private TextView text(String value,int size,int color){
        TextView view=new TextView(getContext());view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;
    }
    private void add(LinearLayout parent,View child,int top){
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin=dp(top);parent.addView(child,params);
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout card=new LinearLayout(getContext());card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(16),dp(18),dp(18));card.setBackground(background(PANEL,22));add(parent,card,16);return card;
    }
    private EditText field(LinearLayout parent,String label,String hint,int type,String tag){
        add(parent,text(label,13,MUTED),14);
        EditText input=new EditText(getContext());input.setTag(tag);input.setContentDescription(label);
        input.setInputType(type);input.setSingleLine(true);input.setTextSize(16);input.setTextColor(TEXT);input.setHintTextColor(0xff829cb7);
        input.setHint(hint);input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setMinHeight(dp(50));input.setBackground(background(FIELD,12));
        input.setSaveEnabled(false);input.setFreezesText(false);add(parent,input,6);return input;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Window window=getWindow();if(window==null)return;
        window.setBackgroundDrawable(background(BG,0));window.setStatusBarColor(BG);window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(0);
        LinearLayout root=new LinearLayout(getContext());root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        if(Build.VERSION.SDK_INT>=30){
            window.setDecorFitsSystemWindows(false);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        }else window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout toolbar=new LinearLayout(getContext());toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(8),dp(6),dp(16),dp(6));
        TextView back=text("‹",32,TEXT);back.setGravity(Gravity.CENTER);back.setContentDescription("Torna alle impostazioni");back.setOnClickListener(v->dismiss());toolbar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text("Account e fornitura",20,TEXT);title.setTypeface(null,Typeface.BOLD);toolbar.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(toolbar);
        ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setTag("account-scroll");root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout content=new LinearLayout(getContext());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),0,dp(20),dp(26));scroll.addView(content);

        LinearLayout providerCard=card(content);providerCard.setTag("provider-card");
        TextView providerLabel=text("Gestore",15,TEXT);providerLabel.setTypeface(null,Typeface.BOLD);add(providerCard,providerLabel,0);
        FrameLayout choice=new FrameLayout(getContext());choice.setBackground(background(FIELD,12));add(providerCard,choice,10);
        Spinner provider=new Spinner(getContext(),Spinner.MODE_DROPDOWN);provider.setTag("account-provider");provider.setContentDescription("Gestore della fornitura");provider.setBackgroundColor(Color.TRANSPARENT);provider.setPadding(0,0,dp(36),0);
        provider.setAdapter(new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_item,new String[]{"Enel"}){
            @Override public View getView(int position,View recycled,ViewGroup parent){TextView label=text(getItem(position),16,TEXT);label.setPadding(dp(12),dp(12),dp(12),dp(12));label.setGravity(Gravity.CENTER_VERTICAL);return label;}
            @Override public View getDropDownView(int position,View recycled,ViewGroup parent){TextView label=text(getItem(position),16,TEXT);label.setPadding(dp(16),dp(16),dp(16),dp(16));label.setBackgroundColor(FIELD);return label;}
        });
        provider.setPopupBackgroundDrawable(background(FIELD,12));choice.addView(provider,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        ImageView arrow=new ImageView(getContext());arrow.setImageResource(R.drawable.account_chevron_down);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);arrow.setOnClickListener(v->provider.performClick());FrameLayout.LayoutParams arrowParams=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.END|Gravity.CENTER_VERTICAL);arrowParams.rightMargin=dp(12);choice.addView(arrow,arrowParams);

        LinearLayout account=card(content);account.setTag("account-card");TextView heading=text("Account Enel e dati della fornitura",15,TEXT);heading.setTypeface(null,Typeface.BOLD);add(account,heading,0);
        user=field(account,"Email account Enel","nome@esempio.it",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,"account-user");
        password=field(account,"Password","Password del tuo account",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,"account-password");
        TextView reveal=text("Mostra password",12,TEAL);reveal.setTag("account-reveal");reveal.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);reveal.setMinHeight(dp(44));reveal.setContentDescription("Mostra password");
        reveal.setOnClickListener(v->{boolean masked=password.getTransformationMethod() instanceof PasswordTransformationMethod;password.setTransformationMethod(masked?null:PasswordTransformationMethod.getInstance());reveal.setText(masked?"Nascondi password":"Mostra password");reveal.setContentDescription(reveal.getText());password.setSelection(password.length());});add(account,reveal,0);
        pod=field(account,"POD","IT… · dalla bolletta",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,"account-pod");
        number=field(account,"Numero utente Enel","Dall’Area Clienti Enel",InputType.TYPE_CLASS_TEXT,"account-number");
        TextView optional=text("Codice fiscale · facoltativo  ⌄",12,MUTED);optional.setMinHeight(dp(44));optional.setGravity(Gravity.CENTER_VERTICAL);optional.setTag("account-fiscal-toggle");add(account,optional,4);
        LinearLayout fiscalGroup=new LinearLayout(getContext());fiscalGroup.setOrientation(LinearLayout.VERTICAL);add(account,fiscalGroup,0);
        fiscal=field(fiscalGroup,"Codice fiscale intestatario","Per l’archivio bollette",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,"account-fiscal");
        fiscalGroup.setVisibility(initial!=null&&!initial.fiscalCode.isEmpty()?View.VISIBLE:View.GONE);
        optional.setOnClickListener(v->fiscalGroup.setVisibility(fiscalGroup.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        if(initial!=null){user.setText(initial.username);password.setText(initial.password);pod.setText(initial.pod);number.setText(initial.userNumber);fiscal.setText(initial.fiscalCode);}

        LinearLayout safety=card(content);safety.setTag("account-security");LinearLayout securityTitle=new LinearLayout(getContext());securityTitle.setGravity(Gravity.CENTER_VERTICAL);
        ImageView shield=new ImageView(getContext());shield.setImageResource(R.drawable.account_shield);shield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);securityTitle.addView(shield,new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView safeHeading=text("I tuoi dati restano protetti",14,TEAL);safeHeading.setPadding(dp(9),0,0,0);securityTitle.addView(safeHeading);add(safety,securityTitle,0);
        add(safety,text("La password viene inviata soltanto ai server HTTPS Enel. Nessuna credenziale viene inviata a ChatGPT o GitHub.",12,MUTED),9);
        remember=new CheckBox(getContext());remember.setTag("account-remember");remember.setText("Salva cifrato su questo telefono");remember.setTextSize(14);remember.setTextColor(TEXT);remember.setButtonTintList(ColorStateList.valueOf(TEAL));remember.setChecked(saved);remember.setMinHeight(dp(48));add(safety,remember,10);
        add(safety,text("Il salvataggio permette di aggiornare dal widget. Per cancellare le credenziali già salvate, usa Scollega account negli strumenti.",12,MUTED),2);

        error=text(restoreWarning==null?"":restoreWarning,13,0xffffb5ad);error.setTag("account-error");error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);error.setVisibility(restoreWarning==null?View.GONE:View.VISIBLE);add(content,error,14);
        Button submit=new Button(getContext());submit.setTag("account-connect");submit.setText("Collega e aggiorna");submit.setTextSize(15);submit.setTextColor(TEXT);submit.setAllCaps(false);submit.setMinHeight(dp(52));submit.setBackground(background(0xff0875ff,15));add(content,submit,18);
        submit.setOnClickListener(v->{
            EnelConnector.Credentials credentials=new EnelConnector.Credentials(user.getText().toString(),password.getText().toString(),pod.getText().toString(),number.getText().toString(),fiscal.getText().toString());
            try{credentials.validate();}catch(Exception invalid){error.setText(invalid.getMessage());error.setVisibility(View.VISIBLE);scroll.post(()->scroll.smoothScrollTo(0,error.getTop()));return;}
            boolean persist=remember.isChecked();dismiss();connect.run(credentials,persist);
        });
        TextView disclosure=text("Come funziona il collegamento  ⌄",13,MUTED);disclosure.setMinHeight(dp(48));disclosure.setGravity(Gravity.CENTER_VERTICAL);add(content,disclosure,10);
        TextView explanation=text("Collegamento sperimentale e non ufficiale. L’accesso può richiedere verifiche aggiuntive. Recupera il periodo impostato in Tariffe e lo storico disponibile, incluso il mese corrente.",12,MUTED);explanation.setVisibility(View.GONE);add(content,explanation,0);disclosure.setOnClickListener(v->{boolean open=explanation.getVisibility()!=View.VISIBLE;explanation.setVisibility(open?View.VISIBLE:View.GONE);disclosure.setText(open?"Come funziona il collegamento  ⌃":"Come funziona il collegamento  ⌄");});
        TextView advanced=text("Diagnostica e strumenti account  ›",13,TEAL);advanced.setTag("account-tools");advanced.setMinHeight(dp(48));advanced.setGravity(Gravity.CENTER_VERTICAL);add(content,advanced,4);advanced.setOnClickListener(v->{dismiss();tools.run();});
        setContentView(root);window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);root.requestFocus();root.requestApplyInsets();
    }
}
