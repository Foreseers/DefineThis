package com.foreseer.definethis.UI.HistoryScreen;

import com.foreseer.definethis.Data.Models.DeletedRecord;
import com.foreseer.definethis.Data.Models.Word;
import com.foreseer.definethis.Data.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Konstantin "Foreseer" Buinak on 22.06.2017.
 */

public class HistoryInteractorImpl implements HistoryScreenContract.HistoryInteractor {

    private HistoryInteractorListener listener;
    private List<Word> lastRequested;

    private HistoryScreenContract.SortType lastSorted;

    private Stack<DeletedRecord> deletedRecords;

    private Disposable wordRequest;
    private Disposable deletedRecordsRequest;

    public HistoryInteractorImpl(HistoryInteractorListener listener, HistoryScreenContract.SortType lastSorted) {
        this.listener = listener;
        this.lastSorted = lastSorted;

        lastRequested = new ArrayList<>();
        deletedRecords = new Stack<>();

        requestDeletedRecords();
    }


    private void requestDeletedRecords(){
        deletedRecordsRequest = Observable.just(Repository.getAllDeletedRecords())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(result -> deletedRecords = result);
    }

    @Override
    public void requestDefinitions(HistoryScreenContract.SortType sortType) {
        wordRequest = Observable.just(Repository.getAllWords())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .map(words -> processWords(words, sortType))
                .subscribe(words -> listener.onDefinitionsReceived(words));
    }

    @Override
    public void requestDefinitions() {
        requestDefinitions(lastSorted);
    }

    @Override
    public void querySearch(String searchString) {
        if (searchString.equals("")) {
            requestDefinitions(lastSorted);
            return;
        }

        List<Word> queryResults = new ArrayList<>();

        //first run, include words that start with the search string
        for (Word word : lastRequested) {
            if (word.getWord().startsWith(searchString)) {
                //shouldn't add those already in the list
                if (!queryResults.contains(word)) {
                    queryResults.add(word);
                }
            }
        }

        //second run, include those that contain the search string
        for (Word word : lastRequested) {
            if (word.getWord().contains(searchString)) {
                //shouldn't add those already in the list
                if (!queryResults.contains(word)) {
                    queryResults.add(word);
                }
            }
        }

        listener.onDefinitionsReceived(queryResults);
    }

    @Override
    public void requestUndo() {
        if (!deletedRecords.empty()){
            DeletedRecord record = deletedRecords.pop();
            for (Word word :
                    record.getWords()) {
                lastRequested.add(word);
                Repository.saveWord(word);
            }
            listener.onDefinitionsReceived(processWords(lastRequested, lastSorted));
        }
    }

    @Override
    public void resetHistory() {
        DeletedRecord record = new DeletedRecord(UUID.randomUUID().getMostSignificantBits(), lastRequested);
        deletedRecords.push(record);
        Repository.saveDeletedRecord(record);

        lastRequested = new ArrayList<>();
        Repository.deleteAllWords();
    }

    @Override
    public void finish() {
        requestDispose();
        listener = null;
    }

    private void requestDispose() {
        if (wordRequest != null) {
            wordRequest.dispose();
            wordRequest = null;
        }
        if (deletedRecordsRequest != null){
            deletedRecordsRequest.dispose();
            deletedRecordsRequest = null;
        }
    }

    private List<Word> processWords(List<Word> words, HistoryScreenContract.SortType sortType) {
        List<Word> list = new ArrayList<>();
        list.addAll(words);

        //prepare for searching
        //search is done and output in an alphabetical manner
        lastRequested = new ArrayList<>(words.size());
        lastRequested.addAll(words);
        sortList(HistoryScreenContract.SortType.A_TO_Z, lastRequested);


        lastSorted = sortType;
        if (sortType != null) {
            //very primitive sorting please replace later
            if (sortType == HistoryScreenContract.SortType.A_TO_Z) {
                list = new ArrayList<>(lastRequested.size());
                list.addAll(lastRequested);
            } else {
                sortList(sortType, list);
            }
            // sorting ends here
        }

        return list;
    }

    private void sortList(HistoryScreenContract.SortType sortType, List<Word> list) {
        for (int i = 0; i < (list.size() - 1); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (sortType == HistoryScreenContract.SortType.NEWEST) {
                    if (list.get(i).getDate().before(list.get(j).getDate())) {
                        Word temporary = list.get(i);
                        list.set(i, list.get(j));
                        list.set(j, temporary);
                    }
                } else if (sortType == HistoryScreenContract.SortType.OLDEST) {
                    if (list.get(i).getDate().after(list.get(j).getDate())) {
                        Word temporary = list.get(i);
                        list.set(i, list.get(j));
                        list.set(j, temporary);
                    }
                } else if (sortType == HistoryScreenContract.SortType.A_TO_Z) {
                    if (list.get(i).getWord().compareTo(list.get(j).getWord()) > 0) {
                        Word temporary = list.get(i);
                        list.set(i, list.get(j));
                        list.set(j, temporary);
                    }
                } else if (sortType == HistoryScreenContract.SortType.Z_TO_A) {
                    if (list.get(i).getWord().compareTo(list.get(j).getWord()) < 0) {
                        Word temporary = list.get(i);
                        list.set(i, list.get(j));
                        list.set(j, temporary);
                    }
                }
            }
        }
    }

}
