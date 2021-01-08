package io.mattw.youtube.commentsuite.fxml;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import io.mattw.youtube.commentsuite.FXMLSuite;
import io.mattw.youtube.commentsuite.ImageLoader;
import io.mattw.youtube.commentsuite.util.*;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static javafx.application.Platform.runLater;

/**
 * @author mattwright324
 */
public class SearchYouTube implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    private Location<IpApiProvider, IpApiProvider.Location> location;
    private YouTube youtubeApi;
    private ClipboardUtil clipboardUtil = new ClipboardUtil();
    private BrowserUtil browserUtil = new BrowserUtil();

    @FXML private Pane form;
    @FXML private ImageView searchIcon;
    @FXML private ImageView geoIcon;
    @FXML private ComboBox<String> searchType;
    @FXML private TextField searchText;
    @FXML private Button submit;
    @FXML private HBox locationBox;
    @FXML private TextField searchLocation;
    @FXML private Button geolocate;
    @FXML private ComboBox<String> searchRadius;
    @FXML private ComboBox<String> searchOrder;
    @FXML private ComboBox<String> resultType;
    @FXML private ListView<SearchYouTubeListItem> resultsList;
    @FXML private HBox bottom;
    @FXML private Button btnAddToGroup;
    @FXML private Button btnClear;
    @FXML private Button btnNextPage;
    @FXML private Label searchInfo;

    @FXML private MenuItem menuCopyId;
    @FXML private MenuItem menuOpenBrowser;
    @FXML private MenuItem menuAddToGroup;
    @FXML private MenuItem menuDeselectAll;

    @FXML private OverlayModal<SYAddToGroupModal> addToGroupModal;

    private int total = 0;
    private int number = 0;
    private String emptyToken = "emptyToken";
    private String pageToken = emptyToken;

    private YouTube.Search.List searchList;
    private SimpleBooleanProperty searching = new SimpleBooleanProperty(false);
    private String[] types = {"all", "video", "playlist", "channel"};

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("Initialize SearchYouTube");

        youtubeApi = FXMLSuite.getYouTube();
        this.location = FXMLSuite.getLocation();

        MultipleSelectionModel selectionModel = resultsList.getSelectionModel();

        searchIcon.setImage(ImageLoader.SEARCH.getImage());
        geoIcon.setImage(ImageLoader.LOCATION.getImage());

        BooleanBinding isLocation = searchType.valueProperty().isEqualTo("Location");
        locationBox.managedProperty().bind(isLocation);
        locationBox.visibleProperty().bind(isLocation);
        searchRadius.managedProperty().bind(isLocation);
        searchRadius.visibleProperty().bind(isLocation);

        resultsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        menuCopyId.setOnAction(ae -> {
            List<SearchYouTubeListItem> list = selectionModel.getSelectedItems();
            List<String> ids = list.stream().map(SearchYouTubeListItem::getObjectId).collect(Collectors.toList());
            clipboardUtil.setClipboard(ids);
        });
        menuOpenBrowser.setOnAction(ae -> {
            List<SearchYouTubeListItem> list = selectionModel.getSelectedItems();
            for (SearchYouTubeListItem view : list) {
                browserUtil.open(view.getYoutubeURL());
            }
        });
        menuAddToGroup.setOnAction(ae -> btnAddToGroup.fire());
        menuDeselectAll.setOnAction(ae -> selectionModel.clearSelection());

        btnAddToGroup.disableProperty().bind(selectionModel.selectedIndexProperty().isEqualTo(-1));
        selectionModel.getSelectedItems().addListener((ListChangeListener) (c -> {
            int items = selectionModel.getSelectedItems().size();
            runLater(() -> btnAddToGroup.setText(String.format("Add to Group (%s)", items)));
        }));
        resultsList.itemsProperty().addListener((o, ov, nv) -> runLater(() -> {
            int selectedCount = selectionModel.getSelectedItems().size();
            btnAddToGroup.setText(String.format("Add to Group (%s)", selectedCount));
        }));

        btnClear.setOnAction(ae -> runLater(() -> resultsList.getItems().clear()));

        geolocate.setOnAction(ae -> new Thread(() -> {
            geolocate.setDisable(true);
            try {
                IpApiProvider.Location myLocation = this.location.getMyLocation();

                String coordinates = myLocation.lat + "," + myLocation.lon;

                runLater(() -> searchLocation.setText(coordinates));
            } catch (IOException e) {
                logger.error(e);
            }
            geolocate.setDisable(false);
        }).start());

        bottom.disableProperty().bind(searching);
        form.disableProperty().bind(searching);

        form.setOnKeyPressed(ke -> {
            if (ke.getCode() == KeyCode.ENTER) {
                runLater(() ->
                        submit.fire()
                );
            }
        });
        submit.setOnAction(ae -> {
            total = 0;
            number = 0;
            pageToken = emptyToken;
            resultsList.getItems().clear();
            runLater(() -> searchInfo.setText(String.format("Showing %s out of %s", resultsList.getItems().size(), total)));
            logger.debug("Submit New Search [pageToken={},type={},text={},locText={},locRadius={},order={},result={}]",
                    pageToken, searchType.getValue(), searchText.getText(), searchLocation.getText(),
                    searchRadius.getValue(), searchOrder.getValue(), resultType.getValue());
            new Thread(() ->
                    search(pageToken, searchType.getValue(), searchText.getText(), searchLocation.getText(),
                            searchRadius.getValue(), searchOrder.getValue(),
                            resultType.getSelectionModel().getSelectedIndex())
            ).start();
        });
        btnNextPage.setOnAction(ae ->
                new Thread(() ->
                        search(pageToken, searchType.getValue(), searchText.getText(), searchLocation.getText(),
                                searchRadius.getValue(), searchOrder.getValue(),
                                resultType.getSelectionModel().getSelectedIndex())
                ).start()
        );

        SYAddToGroupModal syAddToGroupModal = new SYAddToGroupModal(resultsList);
        addToGroupModal.setContent(syAddToGroupModal);
        syAddToGroupModal.getBtnClose().setOnAction(ae -> {
            addToGroupModal.setVisible(false);
            addToGroupModal.setManaged(false);
        });
        addToGroupModal.visibleProperty().addListener((cl) -> {
            syAddToGroupModal.getBtnClose().setCancelButton(addToGroupModal.isVisible());
            syAddToGroupModal.getBtnSubmit().setDefaultButton(addToGroupModal.isVisible());
        });
        btnAddToGroup.setOnAction(ae -> {
            syAddToGroupModal.cleanUp();
            addToGroupModal.setVisible(true);
            addToGroupModal.setManaged(true);
        });
    }

    public void search(String pageToken, String type, String text, String locText, String locRadius, String order, int resultType) {
        runLater(() -> searching.setValue(true));
        try {
            String encodedText = URLEncoder.encode(text, "UTF-8");
            String searchType = types[resultType];

            if (pageToken.equals(emptyToken)) {
                pageToken = "";
            }

            searchList = youtubeApi.search().list("snippet")
                    .setKey(FXMLSuite.getYouTubeApiKey())
                    .setMaxResults(50L)
                    .setPageToken(pageToken)
                    .setQ(encodedText)
                    .setType(searchType)
                    .setOrder(order.toLowerCase());

            SearchListResponse sl;
            if (type.equals("Normal")) {
                logger.debug("Normal Search [key={},part=snippet,text={},type={},order={},token={}]",
                        FXMLSuite.getYouTubeApiKey(), encodedText, searchType, order.toLowerCase(), pageToken);

                sl = searchList.execute();
            } else {
                logger.debug("Location Search [key={},part=snippet,text={},loc={},radius={},type={},order={},token={}]",
                        FXMLSuite.getYouTubeApiKey(), encodedText, locText, locRadius, searchType, order.toLowerCase(), pageToken);

                sl = searchList.setLocation(locText)
                        .setLocationRadius(locRadius)
                        .execute();
            }

            this.pageToken = sl.getNextPageToken() == null ? emptyToken : sl.getNextPageToken();
            this.total = sl.getPageInfo().getTotalResults();

            logger.debug("Search [videos={}]", sl.getItems().size());
            for (SearchResult item : sl.getItems()) {
                logger.debug("Video [id={},author={},title={}]",
                        item.getId(),
                        item.getSnippet().getChannelTitle(),
                        item.getSnippet().getTitle());
                SearchYouTubeListItem view = new SearchYouTubeListItem(item, number++);
                runLater(() -> {
                    resultsList.getItems().add(view);
                    searchInfo.setText(String.format("Showing %s out of %s", resultsList.getItems().size(), total));
                });
            }
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
        runLater(() -> {
            if (this.pageToken != null && !this.pageToken.equals(emptyToken)) {
                btnNextPage.setDisable(false);
            }
            searching.setValue(false);
        });
    }
}
