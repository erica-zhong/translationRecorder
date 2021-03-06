package org.wycliffeassociates.translationrecorder.Recording;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.wycliffeassociates.translationrecorder.AudioVisualization.VolumeBar;
import org.wycliffeassociates.translationrecorder.AudioVisualization.WaveformView;
import org.wycliffeassociates.translationrecorder.FilesPage.FileNameExtractor;
import org.wycliffeassociates.translationrecorder.Playback.PlaybackActivity;
import org.wycliffeassociates.translationrecorder.Playback.SourceAudio;
import org.wycliffeassociates.translationrecorder.AudioVisualization.ActiveRecordingRenderer;
import org.wycliffeassociates.translationrecorder.ProjectManager.Project;
import org.wycliffeassociates.translationrecorder.R;
import org.wycliffeassociates.translationrecorder.Reporting.Logger;
import org.wycliffeassociates.translationrecorder.database.ProjectDatabaseHelper;
import org.wycliffeassociates.translationrecorder.project.Chunks;
import org.wycliffeassociates.translationrecorder.wav.WavFile;
import org.wycliffeassociates.translationrecorder.wav.WavMetadata;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RecordingScreen extends Activity implements InsertTaskFragment.Insert {

    public static final String KEY_PROJECT = "key_project";
    public static final String KEY_WAV_FILE = "key_wav_file";
    public static final String KEY_CHAPTER = "key_chapter";
    public static final String KEY_UNIT = "key_unit";
    public static final String KEY_INSERT_LOCATION = "key_insert_location";
    private static final String TAG_INSERT_TASK_FRAGMENT = "insert_task_fragment";
    private static final String STATE_INSERTING = "state_inserting";
    private static final int DEFAULT_CHAPTER = 1;
    private static final int DEFAULT_UNIT = 1;

    //View
    private WaveformView mMainWavView;
    private VolumeBar mVolumeBar;
    private RelativeLayout mToolBar;
    private TextView mSourceView;
    private TextView mLanguageView;
    private TextView mBookView;
    private UnitPicker mUnitPicker;
    private UnitPicker mChapterPicker;

    //Controller
    private ActiveRecordingRenderer manager;

    //State
    private boolean isSaved = false;
    private boolean isRecording = false;
    private boolean isPausedRecording = false;
    private boolean hasStartedRecording = false;
    private boolean isChunkMode = false;
    private boolean mInserting = false;
    private boolean mInsertMode = false;

    private SourceAudio mSrcPlayer;
    private InsertTaskFragment mInsertTaskFragment;
    private ProgressDialog mProgressDialog;
    private WavFile mNewRecording;
    private WavFile mLoadedWav;
    private Project mProject;
    private int mChapter = DEFAULT_CHAPTER;
    private int mUnit = DEFAULT_UNIT;
    private int mInsertLocation = 0;
    private Chunks mChunks;
    private List<Map<String, String>> mChunksList;
    private int mNumChapters;
    private String mStartVerse;
    private String mEndVerse;
    private TextView mModeView;

    public static Intent getInsertIntent(Context ctx, Project project, WavFile wavFile, int chapter, int unit, int locationMs) {
        Logger.w("RecordingScreen", "Creating Insert Intent");
        Intent intent = getRerecordIntent(ctx, project, wavFile, chapter, unit);
        intent.putExtra(KEY_INSERT_LOCATION, locationMs);
        return intent;
    }

    public static Intent getNewRecordingIntent(Context ctx, Project project, int chapter, int unit) {
        Logger.w("RecordingScreen", "Creating New Recording Intent");
        Intent intent = new Intent(ctx, RecordingScreen.class);
        intent.putExtra(KEY_PROJECT, project);
        intent.putExtra(KEY_CHAPTER, chapter);
        intent.putExtra(KEY_UNIT, unit);
        return intent;
    }

    public static Intent getRerecordIntent(Context ctx, Project project, WavFile wavFile, int chapter, int unit) {
        Logger.w("RecordingScreen", "Creating Rerecord Intent");
        Intent intent = getNewRecordingIntent(ctx, project, chapter, unit);
        intent.putExtra(KEY_WAV_FILE, wavFile);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //make sure the tablet does not go to sleep while on the recording screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.recording_screen);

        initialize(getIntent());
        initializeTaskFragment(savedInstanceState);

        manager = new ActiveRecordingRenderer(mMainWavView, mVolumeBar, (TextView)findViewById(R.id.timer_view));
        startService(new Intent(this, WavRecorder.class));
        manager.listenForRecording(true);
        mSrcPlayer.initSrcAudio(mProject, FileNameExtractor.getNameFromProject(mProject, mChapter,
                Integer.parseInt(mStartVerse), Integer.parseInt(mEndVerse)), mChapter);

        Logger.w(this.toString(), "Recording Screen onCreate");
    }

    private void initialize(Intent intent) {
        findViews();
        parseIntent(intent);
        initializeViews();
        setButtonHandlers();
        enableButtons();
        try {
            initializePickers();
            if (mLoadedWav != null) {
                // Take away increment and decrement buttons
                mUnitPicker.displayIncrementDecrement(false);
                mChapterPicker.displayIncrementDecrement(false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseIntent(Intent intent) {
        mProject = intent.getParcelableExtra(KEY_PROJECT);
        mChapter = intent.getIntExtra(KEY_CHAPTER, DEFAULT_CHAPTER);
        mUnit = intent.getIntExtra(KEY_UNIT, DEFAULT_UNIT);
        if (intent.hasExtra(KEY_WAV_FILE)) {
            mLoadedWav = intent.getParcelableExtra(KEY_WAV_FILE);
        }
        if (intent.hasExtra(KEY_INSERT_LOCATION)) {
            mInsertLocation = intent.getIntExtra(KEY_INSERT_LOCATION, 0);
            mInsertMode = true;
        }
        isChunkMode = mProject.getMode().compareTo("chunk") == 0;
    }

    private void initializeTaskFragment(Bundle savedInstanceState) {
        FragmentManager fm = getFragmentManager();
        mInsertTaskFragment = (InsertTaskFragment) fm.findFragmentByTag(TAG_INSERT_TASK_FRAGMENT);
        if (mInsertTaskFragment == null) {
            mInsertTaskFragment = new InsertTaskFragment();
            fm.beginTransaction().add(mInsertTaskFragment, TAG_INSERT_TASK_FRAGMENT).commit();
            fm.executePendingTransactions();
        }
        if (savedInstanceState != null) {
            mInserting = savedInstanceState.getBoolean(STATE_INSERTING, false);
            if (mInserting) {
                displayProgressDialog();
            }
        }
    }

    private void findViews() {
        mSrcPlayer = (SourceAudio) findViewById(R.id.srcAudioPlayer);
        mMainWavView = ((WaveformView) findViewById(R.id.main_canvas));
        mVolumeBar = (VolumeBar) findViewById((R.id.volumeBar1));
        mToolBar = (RelativeLayout) findViewById(R.id.toolbar);
        mBookView = (TextView) findViewById(R.id.file_book);
        mSourceView = (TextView) findViewById(R.id.file_project);
        mLanguageView = (TextView) findViewById(R.id.file_language);
        mUnitPicker = (UnitPicker) findViewById(R.id.unit_picker);
        mChapterPicker = (UnitPicker) findViewById(R.id.chapter_picker);
        mModeView = (TextView) findViewById(R.id.file_unit_label);
        if (mInsertMode) {
            mUnitPicker.displayIncrementDecrement(false);
            mChapterPicker.displayIncrementDecrement(false);
        }
    }

    private void initializeViews() {
        //Logging to help track issue #669
        if(mProject.getSlug().equals("")) {
            Logger.e(this.toString(), "Project book is empty string " + mProject);
        }

        String languageCode = mProject.getTargetLanguage();
        mLanguageView.setText(languageCode.toUpperCase());
        mLanguageView.postInvalidate();

        String bookCode = mProject.getSlug();
        ProjectDatabaseHelper db = new ProjectDatabaseHelper(this);
        String bookName = db.getBookName(bookCode);
        mBookView.setText(bookName);
        mBookView.postInvalidate();

        if (isChunkMode) {
            mModeView.setText("Chunk");
        } else {
            mModeView.setText("Verse");
        }

        if (mInsertMode) {
            mToolBar.setBackgroundColor(getResources().getColor(R.color.secondary));
        }

        mSourceView.setText(mProject.getVersion().toUpperCase());
    }

    private void initializePickers() throws IOException {
        if (mProject.isOBS()) {
            //mNumChapters = OBS_SIZE;
        } else {
            mChunks = new Chunks(this, mProject.getSlug());
            mNumChapters = mChunks.getNumChapters();
            mChunksList = mChunks.getChunks(mProject, mChapter);
        }
        initializeUnitPicker();
        initializeChapterPicker();
    }

    private void initializeUnitPicker() {
        final String[] values = new String[mChunksList.size()];
        if (isChunkMode) {
            setDisplayValuesAsRange(values);
        } else {
            for (int i = 0; i < mChunksList.size(); i++) {
                values[i] = mChunksList.get(i).get(Chunks.FIRST_VERSE);
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Logger.w(this.toString(), "Initializing unit picker");
                if (values != null && values.length > 0) {
                    mUnitPicker.setDisplayedValues(values);
                    mUnitPicker.setCurrent(getChunkIndex(mChunksList, mUnit));
                    setChunk(getChunkIndex(mChunksList, mUnit) + 1);
                    //reinitialize all of the filenames
                    mUnitPicker.setOnValueChangedListener(new UnitPicker.OnValueChangeListener() {
                        @Override
                        public void onValueChange(UnitPicker picker, int oldVal, int newVal) {
                            Logger.w(this.toString(), "User changed unit");
                            setChunk(newVal + 1);
                            mSrcPlayer.reset(mProject, FileNameExtractor.getNameFromProject(mProject, mChapter,
                                    Integer.parseInt(mStartVerse), Integer.parseInt(mEndVerse)), mChapter);
                        }
                    });
                } else {
                    Logger.e(this.toString(), "values was null or of zero length");
                }
            }
        });
    }

    private void initializeChapterPicker() {
        Logger.w(this.toString(), "Initializing chapter picker");
        int numChapters = mChunks.getNumChapters();
        final String[] values = new String[numChapters];
        for (int i = 0; i < numChapters; i++) {
            values[i] = String.valueOf(i + 1);
        }
        if (values != null && values.length > 0) {
            mChapterPicker.setDisplayedValues(values);
            mChapterPicker.setCurrent(mChapter - 1);
            mChapterPicker.setOnValueChangedListener(new UnitPicker.OnValueChangeListener() {
                @Override
                public void onValueChange(UnitPicker picker, int oldVal, int newVal) {
                    Logger.w(this.toString(), "User changed chapter");
                    mUnit = 1;
                    mChapter = newVal + 1;
                    mUnitPicker.setCurrent(0);
                    mChunksList = mChunks.getChunks(mProject, mChapter);
                    initializeUnitPicker();
                    mSrcPlayer.reset(mProject, FileNameExtractor.getNameFromProject(mProject, mChapter,
                            Integer.parseInt(mStartVerse), Integer.parseInt(mEndVerse)), mChapter);
                }
            });
        } else {
            Logger.e(this.toString(), "values was null or of zero length");
        }
    }

    private int getChunkIndex(List<Map<String, String>> chunks, int chunk) {
        for (int i = 0; i < chunks.size(); i++) {
            if (Integer.parseInt(chunks.get(i).get(Chunks.FIRST_VERSE)) == chunk) {
                return i;
            }
        }
        return 1;
    }

    private void setDisplayValuesAsRange(String[] values) {
        Map<String, String> chunk;
        String firstVerse, lastVerse;

        for (int i = 0; i < mChunksList.size(); i++) {
            chunk = mChunksList.get(i);
            firstVerse = chunk.get(Chunks.FIRST_VERSE);
            lastVerse = chunk.get(Chunks.LAST_VERSE);
            if (firstVerse.compareTo(lastVerse) == 0) {
                values[i] = firstVerse;
            } else {
                values[i] = firstVerse.concat("-").concat(lastVerse);
            }
        }
    }

    @Override
    public void onPause() {
        Logger.w(this.toString(), "Recording screen onPause");
        super.onPause();
        if (isRecording) {
            isRecording = false;
            stopService(new Intent(this, WavRecorder.class));
            RecordingQueues.stopQueues(this);
        } else if (isPausedRecording) {
            RecordingQueues.stopQueues(this);
        } else if (!hasStartedRecording) {
            stopService(new Intent(this, WavRecorder.class));
            RecordingQueues.stopVolumeTest();
        }
        mSrcPlayer.pauseSource();
        mSrcPlayer.cleanup();
        finish();
    }

    @Override
    public void onDestroy() {
        Logger.w(this.toString(), "Recording screen onDestroy");
        mSrcPlayer.cleanup();
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(STATE_INSERTING, mInserting);
        Logger.w(this.toString(), "Recording screen onSaveInstanceState");
    }

    public void swapViews(int[] toShow, int[] toHide) {
        for (int v : toShow) {
            View view = findViewById(v);
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
        }
        for (int v : toHide) {
            View view = findViewById(v);
            if (view != null) {
                view.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void pauseRecording() {
        isPausedRecording = true;
        manager.pauseTimer();
        isRecording = false;
        int toShow[] = {R.id.btnRecording, R.id.btnStop};
        int toHide[] = {R.id.btnPauseRecording};
        swapViews(toShow, toHide);
        stopService(new Intent(this, WavRecorder.class));
        RecordingQueues.pauseQueues();
        Logger.w(this.toString(), "Pausing recording");
    }

    private void startRecording() {
        mSrcPlayer.cleanup();
        mSrcPlayer.setEnabled(false);

        // Take away increment and decrement buttons
        mUnitPicker.displayIncrementDecrement(false);
        mChapterPicker.displayIncrementDecrement(false);
        hasStartedRecording = true;
        stopService(new Intent(this, WavRecorder.class));
        int toShow[] = {R.id.btnPauseRecording};
        int toHide[] = {R.id.btnRecording, R.id.btnStop};
        swapViews(toShow, toHide);
        isRecording = true;
        manager.setIsRecording(true);
        Logger.w(this.toString(), "Starting recording");

        if (!isPausedRecording) {
            RecordingQueues.stopVolumeTest();
            manager.startTimer();
            isSaved = false;
            RecordingQueues.clearQueues();
            File file = FileNameExtractor.createFile(mProject, mChapter, Integer.parseInt(mStartVerse), Integer.parseInt(mEndVerse));
            mNewRecording = new WavFile(file, new WavMetadata(mProject, String.valueOf(mChapter), mStartVerse, mEndVerse));
            startService(new Intent(this, WavRecorder.class));
            startService(WavFileWriter.getIntent(this, mNewRecording));
            manager.listenForRecording(false);
        } else {
            manager.resumeTimer();
            isPausedRecording = false;
            startService(new Intent(this, WavRecorder.class));
        }
    }

    private void stopRecording() {
        if (isPausedRecording || isRecording) {
            //Stop recording, load the recorded file, and draw
            stopService(new Intent(this, WavRecorder.class));
            long start = System.currentTimeMillis();
            Logger.w(this.toString(), "Stopping recording");
            RecordingQueues.stopQueues(this);
            Logger.w(this.toString(), "SUCCESS: exited queues, took " + (System.currentTimeMillis() - start) + " to finish writing");
            isRecording = false;
            isPausedRecording = false;
            addTakeToDb();
            mNewRecording.parseHeader();
            if (mInsertMode) {
                finalizeInsert(mLoadedWav, mNewRecording, mInsertLocation);
            } else {
                Intent intent = PlaybackActivity.getPlaybackIntent(this, mNewRecording, mProject, mChapter, mUnit);
                startActivity(intent);
                this.finish();
            }
        }
    }

    private void addTakeToDb() {
        ProjectDatabaseHelper db = new ProjectDatabaseHelper(this);
        FileNameExtractor fne = new FileNameExtractor(mNewRecording.getFile());
        db.addTake(fne, mNewRecording.getFile().getName(), mNewRecording.getMetadata().getMode(), mNewRecording.getFile().lastModified(), 0);
        db.close();
    }

    @Override
    public void onBackPressed() {
        Logger.w(this.toString(), "User pressed back");
        if (!isSaved && hasStartedRecording) {
            FragmentManager fm = getFragmentManager();
            FragmentExitDialog d = new FragmentExitDialog();
            d.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
            d.show(fm, "Exit Dialog");
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Sets the chunk by indexing the chunk list with the provided index
     *
     * @param idx
     */
    private void setChunk(int idx) {
        if (mChunks != null) {
            mStartVerse = mChunksList.get(idx - 1).get(Chunks.FIRST_VERSE);
            mEndVerse = mChunksList.get(idx - 1).get(Chunks.LAST_VERSE);
            mUnit = Integer.parseInt(mStartVerse);
        }
    }

    private void displayProgressDialog() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setTitle("Inserting recording");
        mProgressDialog.setMessage("Please wait...");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    private void setButtonHandlers() {
        findViewById(R.id.btnRecording).setOnClickListener(btnClick);
        findViewById(R.id.btnStop).setOnClickListener(btnClick);
        findViewById(R.id.btnPauseRecording).setOnClickListener(btnClick);
    }

    private void enableButton(int id, boolean isEnable) {
        findViewById(id).setEnabled(isEnable);
    }

    private void enableButtons() {
        enableButton(R.id.btnRecording, true);
        enableButton(R.id.btnStop, true);
        enableButton(R.id.btnPauseRecording, true);
    }

    private void finalizeInsert(WavFile base, WavFile insertClip, int insertFrame) {
        //need to reparse the sizes after recording; updates to the object aren't reflected due to parceling to the writing service
        mNewRecording.parseHeader();
        mLoadedWav.parseHeader();
        mInserting = true;
        displayProgressDialog();
        writeInsert(base, insertClip, insertFrame);
    }

    @Override
    public void writeInsert(WavFile base, WavFile insertClip, int insertFrame) {
        mInsertTaskFragment.writeInsert(base, insertClip, insertFrame);
    }

    public void insertCallback(WavFile result) {
        mInserting = false;
        try {
            mProgressDialog.dismiss();
        } catch (IllegalArgumentException e) {
            Logger.e(this.toString(), "Tried to dismiss insert progress dialog", e);
        }
        Intent intent = PlaybackActivity.getPlaybackIntent(this, result, mProject, mChapter, mUnit);
        startActivity(intent);
        this.finish();
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnRecording: {
                    Logger.w(this.toString(), "User pressed Record");
                    startRecording();
                    break;
                }
                case R.id.btnStop: {
                    Logger.w(this.toString(), "User pressed Stop");
                    stopRecording();
                    break;
                }
                case R.id.btnPauseRecording: {
                    Logger.w(this.toString(), "User pressed Pause");
                    pauseRecording();
                    break;
                }
            }
        }
    };
}
