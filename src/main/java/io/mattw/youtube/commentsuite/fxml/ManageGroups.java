package io.mattw.youtube.commentsuite.fxml;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.mattw.youtube.commentsuite.FXMLSuite;
import io.mattw.youtube.commentsuite.ImageLoader;
import io.mattw.youtube.commentsuite.db.CommentDatabase;
import io.mattw.youtube.commentsuite.db.Group;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionModel;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

import static javafx.application.Platform.runLater;

/**
 * Manages group selection, creation, and content switching.
 *
 * @author mattwright324
 */
public class ManageGroups implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    Cache<String, ManageGroupsManager> managerCache = CacheBuilder.newBuilder().build();

    private CommentDatabase database;

    @FXML private OverlayModal<MGCreateGroupModal> overlayModal;

    @FXML private ImageView plusIcon;
    @FXML private ComboBox<Group> comboGroupSelect;
    @FXML private Button btnCreateGroup;
    @FXML private Pane content;

    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("Initialize ManageGroups");

        database = FXMLSuite.getDatabase();

        /*
         * Logic for main pane.
         */

        plusIcon.setImage(ImageLoader.PLUS.getImage());

        SelectionModel<Group> selectionModel = comboGroupSelect.getSelectionModel();
        comboGroupSelect.setItems(database.getGlobalGroupList());
        new Thread(() -> {
            try {
                database.refreshGroups();
            } catch (SQLException e) {
                logger.error(e);
            }
        }).start();
        comboGroupSelect.getItems().addListener((ListChangeListener<Group>) (c -> {
            if (!comboGroupSelect.getItems().isEmpty() && selectionModel.getSelectedIndex() == -1) {
                selectionModel.select(0);
            }
        }));
        selectionModel.selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null) {
                ManageGroupsManager manager = managerCache.getIfPresent(nv.getId());
                if (manager != null) {
                    runLater(() -> {
                        content.getChildren().clear();
                        content.getChildren().addAll(manager);
                    });
                } else {
                    try {
                        ManageGroupsManager m = new ManageGroupsManager(selectionModel.getSelectedItem());
                        managerCache.put(nv.getId(), m);
                        runLater(() -> {
                            content.getChildren().clear();
                            content.getChildren().addAll(m);
                        });
                    } catch (IOException e) {
                        logger.error(e);
                    }
                }
            } else {
                managerCache.invalidateAll();

                try {
                    database.refreshGroups();
                } catch (SQLException e) {
                    logger.error("Failed to refresh groups", e);
                }
            }
        });

        /*
         * Logic for Create Group popup.
         */
        MGCreateGroupModal modal = new MGCreateGroupModal();
        overlayModal.setContent(modal);
        btnCreateGroup.setOnAction(ae -> runLater(() -> {
            modal.cleanUp();
            overlayModal.setVisible(true);
        }));
        overlayModal.visibleProperty().addListener((cl) -> {
            modal.getBtnClose().setCancelButton(overlayModal.isVisible());
            modal.getBtnSubmit().setDefaultButton(overlayModal.isVisible());
        });
        modal.getBtnClose().setOnAction(ae -> runLater(() ->
                overlayModal.setVisible(false))
        );
        modal.getBtnSubmit().setOnAction(ae -> new Thread(() -> {
            logger.debug("Attempting to create group");
            runLater(() -> overlayModal.setDisable(true));
            String name = modal.getNameField().getText();
            if (!name.isEmpty()) {
                try {
                    Group g = database.createGroup(name);
                    logger.debug("Created new group [id={},name={}]", g.getId(), g.getName());
                    runLater(() -> {
                        comboGroupSelect.getSelectionModel().select(g);
                        overlayModal.setDisable(false);
                        modal.getErrorMsg().setManaged(false);
                        overlayModal.setVisible(false);
                    });
                } catch (SQLException e) {
                    logger.error(e);
                    runLater(() -> {
                        overlayModal.setDisable(false);
                        modal.getErrorMsg().setManaged(true);
                        modal.getErrorMsg().setText("Name already exists, try another!");
                    });
                }
            } else {
                runLater(() -> {
                    overlayModal.setDisable(false);
                    modal.getErrorMsg().setManaged(true);
                    modal.getErrorMsg().setText("Name must not be empty.");
                });
            }
        }).start());
    }
}
