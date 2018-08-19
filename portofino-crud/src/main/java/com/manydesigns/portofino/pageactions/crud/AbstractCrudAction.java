/*
 * Copyright (C) 2005-2017 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package com.manydesigns.portofino.pageactions.crud;

import com.manydesigns.elements.ElementsThreadLocals;
import com.manydesigns.elements.FormElement;
import com.manydesigns.elements.Mode;
import com.manydesigns.elements.annotations.*;
import com.manydesigns.elements.blobs.Blob;
import com.manydesigns.elements.blobs.BlobManager;
import com.manydesigns.elements.blobs.BlobUtils;
import com.manydesigns.elements.fields.*;
import com.manydesigns.elements.forms.FieldSet;
import com.manydesigns.elements.forms.*;
import com.manydesigns.elements.messages.SessionMessages;
import com.manydesigns.elements.options.*;
import com.manydesigns.elements.reflection.ClassAccessor;
import com.manydesigns.elements.reflection.PropertyAccessor;
import com.manydesigns.elements.servlet.MutableHttpServletRequest;
import com.manydesigns.elements.servlet.UrlBuilder;
import com.manydesigns.elements.text.OgnlTextFormat;
import com.manydesigns.elements.util.FormUtil;
import com.manydesigns.elements.util.MimeTypes;
import com.manydesigns.elements.util.ReflectionUtil;
import com.manydesigns.elements.util.Util;
import com.manydesigns.elements.xml.XhtmlBuffer;
import com.manydesigns.portofino.PortofinoProperties;
import com.manydesigns.portofino.buttons.ButtonInfo;
import com.manydesigns.portofino.buttons.ButtonsLogic;
import com.manydesigns.portofino.buttons.GuardType;
import com.manydesigns.portofino.buttons.annotations.Guard;
import com.manydesigns.portofino.di.Inject;
import com.manydesigns.portofino.modules.BaseModule;
import com.manydesigns.portofino.pageactions.AbstractPageAction;
import com.manydesigns.portofino.pageactions.PageActionLogic;
import com.manydesigns.portofino.pageactions.PageInstance;
import com.manydesigns.portofino.pageactions.annotations.ConfigurationClass;
import com.manydesigns.portofino.pageactions.annotations.SupportsDetail;
import com.manydesigns.portofino.pageactions.crud.configuration.CrudConfiguration;
import com.manydesigns.portofino.pageactions.crud.configuration.CrudProperty;
import com.manydesigns.portofino.pageactions.crud.reflection.CrudAccessor;
import com.manydesigns.portofino.security.AccessLevel;
import com.manydesigns.portofino.security.RequiresPermissions;
import com.manydesigns.portofino.security.SecurityLogic;
import com.manydesigns.portofino.security.SupportsPermissions;
import com.manydesigns.portofino.util.PkHelper;
import com.manydesigns.portofino.util.ShortNameUtils;
import ognl.OgnlContext;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.shiro.SecurityUtils;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A generic PageAction offering CRUD functionality, independently on the underlying data source.</p>
 * <p>Out of the box, instances of this class are capable of the following:</p>
 *   <ul>
 *      <li>Presenting search, create, read, delete, update operations (the last two also in bulk mode) to the user,
 *          while delegating the actual implementation (e.g. accessing a database table, calling a web service,
 *          querying a JSON data source, etc.) to concrete subclasses;
 *      </li>
 *      <li>Performing exports to various formats (Pdf, Excel) of the Read view and the Search view,
 *          with the possibility for subclasses to customize the exports;</li>
 *      <li>Managing selection providers to constrain certain properties to values taken from a list, and aid
 *          the user in inserting those values (e.g. picking colours from a combo box, or cities with an
 *          autocompleted input field); the actual handling of selection providers is delegated to a
 *          companion object of type {@link SelectionProviderSupport} which must be provided by the concrete
 *          subclasses;</li>
 *      <li>Handling permissions so that only enabled users may create, edit or delete objects;</li>
 *      <li>Offering hooks for subclasses to easily customize certain key functions (e.g. execute custom code
 *          before or after saving an object).</li>
 *   </ul>
 * <p>This PageAction can handle a varying number of URL path parameters. Each parameter is assumed to be part
 * of an object identifier - for example, a database primary key (single or multi-valued). When no parameter is
 * specified, the page is in search mode. When the correct number of parameters is provided, the action attempts
 * to load an object with the appropriate identifier (for example, by loading a row from a database table with
 * the corresponding primary key). As any other page, crud pages can have children, and they always prevail over
 * the object key: a crud page with a child named &quot;child&quot; will never attempt to load an object with key
 * &quot;child&quot;.</p>
 * <!-- TODO popup mode -->
 *
 * @param <T> the types of objects that this crud can handle.
 *
 * @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
 * @author Angelo Lupo          - angelo.lupo@manydesigns.com
 * @author Giampiero Granatella - giampiero.granatella@manydesigns.com
 * @author Alessio Stalla       - alessio.stalla@manydesigns.com
 */
@SupportsPermissions({ CrudAction.PERMISSION_CREATE, CrudAction.PERMISSION_EDIT, CrudAction.PERMISSION_DELETE })
@RequiresPermissions(level = AccessLevel.VIEW)
@ConfigurationClass(CrudConfiguration.class)
@SupportsDetail
public abstract class AbstractCrudAction<T> extends AbstractPageAction {
    public static final String copyright =
            "Copyright (C) 2005-2017 ManyDesigns srl";

    public final static String SEARCH_STRING_PARAM = "searchString";
    public final static String prefix = "";
    public final static String searchPrefix = prefix + "search_";

    //**************************************************************************
    // Permissions
    //**************************************************************************

    /**
     * Constants for the permissions supported by instances of this class. Subclasses are recommended to
     * support at least the permissions defined here.
     */
    public static final String
            PERMISSION_CREATE = "crud-create",
            PERMISSION_EDIT = "crud-edit",
            PERMISSION_DELETE = "crud-delete";

    public static final Logger logger =
            LoggerFactory.getLogger(AbstractCrudAction.class);

    //--------------------------------------------------------------------------
    // Web parameters
    //--------------------------------------------------------------------------

    public String[] pk;
    public String propertyName;
    public String[] selection;
    public String searchString;
    public String successReturnUrl;
    public Integer firstResult;
    public Integer maxResults;
    public String sortProperty;
    public String sortDirection;
    public boolean searchVisible;

    //--------------------------------------------------------------------------
    // Popup
    //--------------------------------------------------------------------------

    protected String popupCloseCallback;

    //--------------------------------------------------------------------------
    // UI forms
    //--------------------------------------------------------------------------

    public SearchForm searchForm;
    public TableForm tableForm;
    public Form form;

    //--------------------------------------------------------------------------
    // Selection providers
    //--------------------------------------------------------------------------

    protected SelectionProviderSupport selectionProviderSupport;

    protected String relName;
    protected int selectionProviderIndex;
    protected String labelSearch;

    //--------------------------------------------------------------------------
    // Data objects
    //--------------------------------------------------------------------------

    public ClassAccessor classAccessor;
    public PkHelper pkHelper;

    public T object;
    public List<? extends T> objects;

    @Inject(BaseModule.DEFAULT_BLOB_MANAGER)
    protected BlobManager blobManager;

    @Inject(BaseModule.TEMPORARY_BLOB_MANAGER)
    protected BlobManager temporaryBlobManager;

    //--------------------------------------------------------------------------
    // Configuration
    //--------------------------------------------------------------------------

    public CrudConfiguration crudConfiguration;
    public Form crudConfigurationForm;
    public TableForm propertiesTableForm;
    public CrudPropertyEdit[] propertyEdits;
    public TableForm selectionProvidersForm;
    public CrudSelectionProviderEdit[] selectionProviderEdits;

    //--------------------------------------------------------------------------
    // Navigation
    //--------------------------------------------------------------------------

    protected ResultSetNavigation resultSetNavigation;

    //--------------------------------------------------------------------------
    // Crud operations
    //--------------------------------------------------------------------------

    /**
     * Loads a list of objects filtered using the current search criteria and limited by the current
     * first and max results parameters. If the load is successful, the implementation must assign
     * the result to the <code>objects</code> field.
     */
    public abstract void loadObjects();

    /**
     * Loads an object by its identifier and returns it. The object must satisfy the current search criteria.
     * @param pkObject the object used as an identifier; the actual implementation is regulated by subclasses.
     * The only constraint is that it is serializable.
     * @return the loaded object, or null if it couldn't be found or it didn't satisfy the search criteria.
     */
    protected abstract T loadObjectByPrimaryKey(Serializable pkObject);

    /**
     * Saves a new object to the persistent storage. The actual implementation is left to subclasses.
     * @param object the object to save.
     * @throws RuntimeException if the object could not be saved.
     */
    protected abstract void doSave(T object);

    /**
     * Saves an existing object to the persistent storage. The actual implementation is left to subclasses.
     * @param object the object to update.
     * @throws RuntimeException if the object could not be saved.
     */
    protected abstract void doUpdate(T object);

    /**
     * Deletes an object from the persistent storage. The actual implementation is left to subclasses.
     * @param object the object to delete.
     * @throws RuntimeException if the object could not be deleted.
     */
    protected abstract void doDelete(T object);

    /**
     * {@link #loadObjectByPrimaryKey(java.io.Serializable)}
     * @param identifier the object identifier in String form
     */
    protected void loadObject(String... identifier) {
        Serializable pkObject = pkHelper.getPrimaryKey(identifier);
        object = loadObjectByPrimaryKey(pkObject);
    }

    //**************************************************************************
    // Search
    //**************************************************************************

    protected void executeSearch() {
        setupSearchForm();
        if(maxResults == null) {
            //Load only the first page if the crud is paginated
            maxResults = getCrudConfiguration().getRowsPerPage();
        }
        loadObjects();
        setupTableForm(Mode.VIEW);
        BlobUtils.loadBlobs(tableForm, getBlobManager(), false);
    }

    public Response jsonSearchData() throws JSONException {
        executeSearch();

        final long totalRecords = getTotalSearchRecords();

        setupTableForm(Mode.VIEW);
        BlobUtils.loadBlobs(tableForm, getBlobManager(), false);
        JSONStringer js = new JSONStringer();
        js.object()
                .key("recordsReturned")
                .value(objects.size())
                .key("totalRecords")
                .value(totalRecords)
                .key("startIndex")
                .value(firstResult == null ? 0 : firstResult)
                .key("Result")
                .array();
        for (TableForm.Row row : tableForm.getRows()) {
            js.object()
                    .key("__rowKey")
                    .value(row.getKey());
            FormUtil.fieldsToJson(js, row);
            js.endObject();
        }
        js.endArray();
        js.endObject();
        String jsonText = js.toString();
        Response.ResponseBuilder builder = Response.ok(jsonText).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8");
        Integer rowsPerPage = getCrudConfiguration().getRowsPerPage();
        if(rowsPerPage != null && totalRecords > rowsPerPage) {
            int firstResult = getFirstResult() != null ? getFirstResult() : 1;
            int currentPage = firstResult / rowsPerPage;
            int lastPage = (int) (totalRecords / rowsPerPage);
            if(totalRecords % rowsPerPage == 0) {
                lastPage--;
            }
            StringBuilder sb = new StringBuilder();
            if(currentPage > 0) {
                sb.append("<").append(getLinkToPage(0)).append(">; rel=\"first\", ");
                sb.append("<").append(getLinkToPage(currentPage - 1)).append(">; rel=\"prev\"");
            }
            if(currentPage != lastPage) {
                if(currentPage > 0) {
                    sb.append(", ");
                }
                sb.append("<").append(getLinkToPage(currentPage + 1)).append(">; rel=\"next\", ");
                sb.append("<").append(getLinkToPage(lastPage)).append(">; rel=\"last\"");
            }
            builder.header("Link", sb.toString());
        }
        return builder.build();
    }

    /**
     * Returns the number of objects matching the current search criteria, not considering set limits
     * (first and max results).
     * @return the number of objects.
     */
    public abstract long getTotalSearchRecords();

    //**************************************************************************
    // Read
    //**************************************************************************

    public Response jsonReadData() throws JSONException {
        if(object == null) {
            throw new IllegalStateException("Object not loaded. Are you including the primary key in the URL?");
        }

        setupForm(Mode.VIEW);
        form.readFromObject(object);
        BlobUtils.loadBlobs(form, getBlobManager(), false);
        refreshBlobDownloadHref();
        String jsonText = FormUtil.writeToJson(form);
        return Response.ok(jsonText).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    }

    public Response jsonEditData() throws JSONException {
        if(object == null) {
            throw new IllegalStateException("Object not loaded. Are you including the primary key in the URL?");
        }
        preEdit();
        BlobUtils.loadBlobs(form, getBlobManager(), false);
        refreshBlobDownloadHref();
        String jsonText = FormUtil.writeToJson(form);
        return Response.ok(jsonText).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    }

    public Response jsonCreateData() throws JSONException {
        preCreate();
        BlobUtils.loadBlobs(form, getBlobManager(), false);
        refreshBlobDownloadHref();
        String jsonText = FormUtil.writeToJson(form);
        return Response.ok(jsonText).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    }


    //**************************************************************************
    // Form handling
    //**************************************************************************

    /**
     * Writes the contents of the create or edit form into the persistent object.
     * Assumes that the form has already been validated.
     * Also processes rich-text (HTML) fields by cleaning the submitted HTML according
     * to the {@link #getWhitelist() whitelist}.
     */
    protected void writeFormToObject() {
        form.writeToObject(object);
        for(TextField textField : FormUtil.collectEditableRichTextFields(form)) {
            //TODO in bulk edit mode, the field should be skipped altogether if the checkbox is not checked.
            PropertyAccessor propertyAccessor = textField.getPropertyAccessor();
            String stringValue = (String) propertyAccessor.get(object);
            String cleanText;
            try {
                Whitelist whitelist = getWhitelist();
                cleanText = Jsoup.clean(stringValue, whitelist);
            } catch (Throwable t) {
                logger.error("Could not clean HTML, falling back to escaped text", t);
                cleanText = StringEscapeUtils.escapeHtml(stringValue);
            }
            propertyAccessor.set(object, cleanText);
        }
    }

    /**
     * Returns the JSoup whitelist used to clean user-provided HTML in rich-text fields.
     * @return the default implementation returns the "basic" whitelist ({@link Whitelist#basic()}).
     */
    protected Whitelist getWhitelist() {
        return Whitelist.basic();
    }

    //**************************************************************************
    // Create/Save
    //**************************************************************************

    protected void preCreate() {
        setupForm(Mode.CREATE);
        object = (T) classAccessor.newInstance();
        createSetup(object);
        form.readFromObject(object);
    }

    protected void saveTemporaryBlobs() {
        try {
            BlobUtils.saveBlobs(form, getTemporaryBlobManager());
        } catch (IOException e1) {
            logger.warn("Could not save temporary blobs", e1);
        }
    }

    //**************************************************************************
    // Edit/Update
    //**************************************************************************

    protected void preEdit() {
        setupForm(Mode.EDIT);
        editSetup(object);
        form.readFromObject(object);
    }

    protected void persistNewBlobs(List<Blob> blobsBefore, List<Blob> blobsAfter) throws IOException {
        for(FileBlobField field : getBlobFields()) {
            Blob blob = field.getValue();
            if(blobsAfter.contains(blob) && !blobsBefore.contains(blob)) {
                getBlobManager().save(blob);
            }
        }
    }

    protected void deleteOldBlobs(List<Blob> blobsBefore, List<Blob> blobsAfter) {
        List<Blob> toDelete = new ArrayList<Blob>(blobsBefore);
        toDelete.removeAll(blobsAfter);
        for(Blob blob : toDelete) {
            try {
                getBlobManager().delete(blob);
            } catch (IOException e) {
                logger.warn("Could not delete blob: " + blob.getCode(), e);
            }
        }
    }

    //**************************************************************************
    // Bulk Edit/Update
    //**************************************************************************

    public boolean isBulkOperationsEnabled() {
        return objects != null && !objects.isEmpty();
    }

    //**************************************************************************
    // Delete
    //**************************************************************************

   //**************************************************************************
    // Hooks/scripting
    //**************************************************************************

    public boolean isCreateEnabled() {
        return true;
    }
    
    /**
     * Hook method called just after a new object has been created.
     * @param object the new object.
     */
    protected void createSetup(T object) {}

    /**
     * Hook method called after values from the create form have been propagated to the new object.
     * @param object the new object.
     * @return true if the object is to be considered valid, false otherwise. In the latter case, the
     * object will not be saved; it is suggested that the cause of the validation failure be displayed
     * to the user (e.g. by using SessionMessages).
     */
    protected boolean createValidate(T object) {
        return true;
    }

    /**
     * Hook method called just before a new object is actually saved to persistent storage.
     * @param object the new object.
     */
    protected void createPostProcess(T object) {}

    /**
     * Executes any pending updates on persistent objects. E.g. saves them to the database, or calls the
     * appropriate operation of a web service, etc.
     */
    protected void commitTransaction() {}

    public boolean isEditEnabled() {
        return true;
    }
    
    /**
     * Hook method called just before an object is used to populate the edit form.
     * @param object the object.
     */
    protected void editSetup(T object) {}

    /**
     * Hook method called after values from the edit form have been propagated to the object.
     * @param object the object.
     * @return true if the object is to be considered valid, false otherwise. In the latter case, the
     * object will not be saved; it is suggested that the cause of the validation failure be displayed
     * to the user (e.g. by using SessionMessages).
     */
    protected boolean editValidate(T object) {
        return true;
    }

    /**
     * Hook method called just before an existing object is actually saved to persistent storage.
     * @param object the object just edited.
     */
    protected void editPostProcess(T object) {}

    public boolean isDeleteEnabled() {
        return true;
    }
    
    /**
     * Hook method called before an object is deleted.
     * @param object the object.
     * @return true if the delete operation is to be performed, false otherwise. In the latter case,
     * it is suggested that the cause of the validation failure be displayed to the user
     * (e.g. by using SessionMessages).
     */
    protected boolean deleteValidate(T object) {
        return true;
    }

    /**
     * Hook method called just before an object is deleted from persistent storage, but after the doDelete
     * method has been called.
     * @param object the object.
     */
    protected void deletePostProcess(T object) {}

    /**
     * Adds an information message (using {@link SessionMessages}) after successful creation of a new record.
     * By default, the message contains a link to the created object as well as a link for creating a new one.
     */
    protected void addSuccessfulSaveInfoMessage() {
        XhtmlBuffer buffer = new XhtmlBuffer();

        pk = pkHelper.generatePkStringArray(object);
        String readUrl = context.getActionPath() + "/" + getPkForUrl(pk);
        String prettyName = ShortNameUtils.getName(getClassAccessor(), object);
        XhtmlBuffer linkToObjectBuffer = new XhtmlBuffer();
        linkToObjectBuffer.writeAnchor(Util.getAbsoluteUrl(readUrl), prettyName);
        buffer.writeNoHtmlEscape(ElementsThreadLocals.getText("object._.saved", linkToObjectBuffer));

        String createUrl = Util.getAbsoluteUrl(context.getActionPath());
        if(!createUrl.contains("?")) {
            createUrl += "?";
        } else {
            createUrl += "&";
        }
        createUrl += "create=";
        createUrl = appendSearchStringParamIfNecessary(createUrl);
        buffer.write(" ");
        buffer.writeAnchor(createUrl, ElementsThreadLocals.getText("create.another.object"));

        SessionMessages.addInfoMessage(buffer);
    }

    //--------------------------------------------------------------------------
    // Setup
    //--------------------------------------------------------------------------

    @Override
    public void setPageInstance(PageInstance pageInstance) {
        super.setPageInstance(pageInstance);
        this.crudConfiguration = (CrudConfiguration) pageInstance.getConfiguration();

        if (crudConfiguration == null) {
            logger.warn("Crud is not configured: " + pageInstance.getPath());
            return;
        }

        ClassAccessor innerAccessor = prepare(pageInstance);
        if (innerAccessor == null) {
            return;
        }
        classAccessor = new CrudAccessor(crudConfiguration, innerAccessor);
        pkHelper = new PkHelper(classAccessor);
        maxParameters = classAccessor.getKeyProperties().length;
    }

    @Override
    public void parametersAcquired() {
        super.parametersAcquired();
        if(pkHelper == null) {
            return;
        }

        if(!parameters.isEmpty()) {
            String encoding = getUrlEncoding();
            pk = parameters.toArray(new String[parameters.size()]);
            try {
                for(int i = 0; i < pk.length; i++) {
                    pk[i] = URLDecoder.decode(pk[i], encoding);
                }
            } catch (UnsupportedEncodingException e) {
                throw new Error(e);
            }
            OgnlContext ognlContext = ElementsThreadLocals.getOgnlContext();

            Serializable pkObject;
            try {
                pkObject = pkHelper.getPrimaryKey(pk);
            } catch (Exception e) {
                logger.warn("Invalid primary key", e);
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }
            object = loadObjectByPrimaryKey(pkObject);
            if(object != null) {
                ognlContext.put(crudConfiguration.getActualVariable(), object);
                String title = getReadTitle();
                pageInstance.setTitle(title);
                pageInstance.setDescription(title);
            } else {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }
        } else {
            String title = getSearchTitle();
            pageInstance.setTitle(title);
            pageInstance.setDescription(title);
        }
    }

    /**
     * <p>Builds the ClassAccessor used to create, manipulate and introspect persistent objects.</p>
     * <p>This method is called during the prepare phase.</p>
     * @param pageInstance the PageInstance corresponding to this action in the current dispatch.
     * @return the ClassAccessor.
     */
    protected abstract ClassAccessor prepare(PageInstance pageInstance);

    public boolean isConfigured() {
        return (classAccessor != null);
    }

    @Deprecated
    protected void setupPagination() {
        setupResultSetNavigation();
    }

    protected void setupResultSetNavigation() {
        int position = objects.indexOf(object);
        if(position < 0) {
            return;
        }
        int size = objects.size();
        setupResultSetNavigation(position, size);
    }

    protected void setupResultSetNavigation(int position, int size) {
        resultSetNavigation = new ResultSetNavigation();
        resultSetNavigation.setPosition(position);
        resultSetNavigation.setSize(size);
        String baseUrl = calculateBaseSearchUrl();
        if(position > 0) {
            resultSetNavigation.setFirstUrl(generateObjectUrl(baseUrl, 0));
            resultSetNavigation.setPreviousUrl(
                    generateObjectUrl(baseUrl, position - 1));
        }
        if(position < size - 1) {
            resultSetNavigation.setLastUrl(
                    generateObjectUrl(baseUrl, size - 1));
            resultSetNavigation.setNextUrl(
                    generateObjectUrl(baseUrl, position + 1));
        }
    }

    /**
     * Computes the search URL from the current URL. In other words, it removes any /pk trailing path segment from the
     * URL used to access the page.
     * @return the search URL.
     */
    protected String calculateBaseSearchUrl() {
        String baseUrl = Util.getAbsoluteUrl(context.getActionPath());
        if(pk != null) {
            for(int i = 0; i < pk.length; i++) {
                int lastSlashIndex = baseUrl.lastIndexOf('/');
                baseUrl = baseUrl.substring(0, lastSlashIndex);
            }
        }
        return baseUrl;
    }

    protected String generateObjectUrl(String baseUrl, int index) {
        Object o = objects.get(index);
        return generateObjectUrl(baseUrl, o);
    }

    protected String generateObjectUrl(String baseUrl, Object o) {
        String[] objPk = pkHelper.generatePkStringArray(o);
        String url = baseUrl + "/" + getPkForUrl(objPk);
        return appendSearchStringParamIfNecessary(url);
    }

    /**
     * Creates, configures and populates the form used to gather search parameters. If this page is embedded, the form's
     * values are not read from the request to avoid having the embedding page influence this one.
     */
    protected void setupSearchForm() {
        SearchFormBuilder searchFormBuilder = createSearchFormBuilder();
        searchForm = buildSearchForm(configureSearchFormBuilder(searchFormBuilder));

        if(!PageActionLogic.isEmbedded(this)) {
            logger.debug("Search form not embedded, no risk of clashes - reading parameters from request");
            readSearchFormFromRequest();
        }
    }

    /**
     * Populates the search form from request parameters.
     * <ul>
     *     <li>If <code>searchString</code> is blank, then the form is read from the request
     *     (by {@link SearchForm#readFromRequest(javax.servlet.http.HttpServletRequest)}) and <code>searchString</code>
     *     is generated accordingly.</li>
     *     <li>Else, <code>searchString</code> is interpreted as a query string and the form is populated from it.</li>
     * </ul>
     */
    protected void readSearchFormFromRequest() {
        if (StringUtils.isBlank(searchString)) {
            searchForm.readFromRequest(context.getRequest());
            searchString = searchForm.toSearchString(getUrlEncoding());
            if (searchString.length() == 0) {
                searchString = null;
            } else {
                searchVisible = true;
            }
        } else {
            MutableHttpServletRequest dummyRequest = new MutableHttpServletRequest();
            String[] parts = searchString.split(",");
            Pattern pattern = Pattern.compile("(.*)=(.*)");
            for (String part : parts) {
                Matcher matcher = pattern.matcher(part);
                if (matcher.matches()) {
                    String key = matcher.group(1);
                    String value = matcher.group(2);
                    logger.debug("Matched part: {}={}", key, value);
                    try {
                        dummyRequest.addParameter(URLDecoder.decode(key, getUrlEncoding()), URLDecoder.decode(value, getUrlEncoding()));
                    } catch (UnsupportedEncodingException e) {
                        logger.error("Unsupported encoding when parsing search string", e);
                    }
                } else {
                    logger.debug("Could not match part: {}", part);
                }
            }
            searchForm.readFromRequest(dummyRequest);
            searchVisible = true;
        }
    }

    protected SearchFormBuilder createSearchFormBuilder() {
        return new SearchFormBuilder(classAccessor);
    }

    protected SearchFormBuilder configureSearchFormBuilder(SearchFormBuilder searchFormBuilder) {
        // setup option providers
        if(selectionProviderSupport != null) {
            for (CrudSelectionProvider current : selectionProviderSupport.getCrudSelectionProviders()) {
                SelectionProvider selectionProvider = current.getSelectionProvider();
                if(selectionProvider == null) {
                    continue;
                }
                String[] fieldNames = current.getFieldNames();
                searchFormBuilder.configSelectionProvider(selectionProvider, fieldNames);
            }
        }
        return searchFormBuilder.configPrefix(searchPrefix);
    }

    protected SearchForm buildSearchForm(SearchFormBuilder searchFormBuilder) {
        return searchFormBuilder.build();
    }

    protected void setupTableForm(Mode mode) {
        int nRows;
        if (objects == null) {
            nRows = 0;
        } else {
            nRows = objects.size();
        }
        TableFormBuilder tableFormBuilder = createTableFormBuilder();
        configureTableFormBuilder(tableFormBuilder, mode, nRows);
        tableForm = buildTableForm(tableFormBuilder);

        if (objects != null) {
            tableForm.readFromObject(objects);
            refreshTableBlobDownloadHref();
        }
    }

    protected void configureTableFormSelectionProviders(TableFormBuilder tableFormBuilder) {
        if(selectionProviderSupport == null) {
            return;
        }
        // setup option providers
        for (CrudSelectionProvider current : selectionProviderSupport.getCrudSelectionProviders()) {
            SelectionProvider selectionProvider = current.getSelectionProvider();
            if(selectionProvider == null) {
                continue;
            }
            String[] fieldNames = current.getFieldNames();
            tableFormBuilder.configSelectionProvider(selectionProvider, fieldNames);
        }
    }

    protected void configureDetailLink(TableFormBuilder tableFormBuilder) {
        boolean isShowingKey = false;
        for (PropertyAccessor property : classAccessor.getKeyProperties()) {
            if(tableFormBuilder.getPropertyAccessors().contains(property) &&
               tableFormBuilder.isPropertyVisible(property)) {
                isShowingKey = true;
                break;
            }
        }

        OgnlTextFormat hrefFormat = getReadURLFormat();

        if(isShowingKey) {
            logger.debug("TableForm: configuring detail links for primary key properties");
            for (PropertyAccessor property : classAccessor.getKeyProperties()) {
                tableFormBuilder.configHrefTextFormat(property.getName(), hrefFormat);
            }
        } else {
            logger.debug("TableForm: configuring detail link for the first visible property");
            for (PropertyAccessor property : classAccessor.getProperties()) {
                if(tableFormBuilder.getPropertyAccessors().contains(property) &&
                   tableFormBuilder.isPropertyVisible(property)) {
                    tableFormBuilder.configHrefTextFormat(
                        property.getName(), hrefFormat);
                    break;
                }
            }
        }
    }

    protected void configureSortLinks(TableFormBuilder tableFormBuilder) {
        for(PropertyAccessor propertyAccessor : classAccessor.getProperties()) {
            String propName = propertyAccessor.getName();
            String sortDirection;
            if(propName.equals(sortProperty) && "asc".equals(this.sortDirection)) {
                sortDirection = "desc";
            } else {
                sortDirection = "asc";
            }

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("sortProperty", propName);
            parameters.put("sortDirection", sortDirection);
            if(!PageActionLogic.isEmbedded(this)) {
                parameters.put(SEARCH_STRING_PARAM, searchString);
            }

            Charset charset = Charset.forName(context.getRequest().getCharacterEncoding());
            UrlBuilder urlBuilder =
                    new UrlBuilder(charset, Util.getAbsoluteUrl(context.getActionPath()), false)
                            .addParameters(parameters);

            XhtmlBuffer xb = new XhtmlBuffer();
            xb.openElement("a");
            xb.addAttribute("class", "sort-link");
            xb.addAttribute("href", urlBuilder.toString());
            xb.writeNoHtmlEscape("%{label}");
            if(propName.equals(sortProperty)) {
                xb.openElement("em");
                xb.addAttribute("class", "pull-right glyphicon glyphicon-chevron-" + ("desc".equals(sortDirection) ? "up" : "down"));
                xb.closeElement("em");
            }
            xb.closeElement("a");
            OgnlTextFormat hrefFormat = OgnlTextFormat.create(xb.toString());
            String encoding = getUrlEncoding();
            hrefFormat.setEncoding(encoding);
            tableFormBuilder.configHeaderTextFormat(propName, hrefFormat);
        }
    }

    public String getLinkToPage(int page) {
        int rowsPerPage = getCrudConfiguration().getRowsPerPage();
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("sortProperty", getSortProperty());
        parameters.put("sortDirection", getSortDirection());
        parameters.put("firstResult", page * rowsPerPage);
        parameters.put("maxResults", rowsPerPage);
        if(!PageActionLogic.isEmbedded(this)) {
            parameters.put(AbstractCrudAction.SEARCH_STRING_PARAM, getSearchString());
        }

        Charset charset = Charset.forName(context.getRequest().getCharacterEncoding());
        UrlBuilder urlBuilder =
                new UrlBuilder(charset, Util.getAbsoluteUrl(context.getActionPath()), false)
                        .addParameters(parameters);
        return urlBuilder.toString();
    }

    protected TableForm buildTableForm(TableFormBuilder tableFormBuilder) {
        TableForm tableForm = tableFormBuilder.build();
        tableForm.setKeyGenerator(pkHelper.createPkGenerator());
        tableForm.setSelectable(tableForm.getRows().length > 0 && isTableFormSelectable());
        tableForm.setCondensed(true);

        return tableForm;
    }

    public Boolean isTableFormSelectable() {
        List<ButtonInfo> buttons = ButtonsLogic.getButtonsForClass(getClass(), "crud-bulk");
        Boolean selectable = false ;
        if(buttons == null) {
            logger.trace("buttons == null");
        } else {
            logger.trace("There are " + buttons.size() + " buttons");
            for(ButtonInfo button : buttons) {
                logger.trace("ButtonInfo: {}", button);
                Method handler = button.getMethod();
                boolean isAdmin = SecurityLogic.isAdministrator(context.getRequest());
                if(!isAdmin &&
                        ((pageInstance != null && !SecurityLogic.hasPermissions(
                                portofinoConfiguration, button.getMethod(), button.getFallbackClass(), pageInstance, SecurityUtils.getSubject())) ||
                                !SecurityLogic.satisfiesRequiresAdministrator(context.getRequest(), this, handler))) {
                    continue;
                }

                if( ButtonsLogic.doGuardsPass(this, handler, GuardType.VISIBLE)
                        && ButtonsLogic.doGuardsPass(this, handler, GuardType.ENABLED)
                        ) {
                    logger.trace("Visible " + button.getButton().key());
                    logger.trace("Guards passed");
                    selectable = true ;
                    break;
                } else {
                    logger.trace("Guards do not pass");
                }
            }
        }
        return selectable;
    }


    protected TableFormBuilder createTableFormBuilder() {
        return new TableFormBuilder(classAccessor);
    }

    /**
     * Configures the builder for the search results form. You can override this method to customize how
     * the form is generated (e.g. adding custom links on specific columns, hiding or showing columns
     * based on some runtime condition, etc.).
     * @param tableFormBuilder the table form builder.
     * @param mode the mode of the form.
     * @param nRows number of rows to display.
     * @return the table form builder.
     */
    protected TableFormBuilder configureTableFormBuilder(TableFormBuilder tableFormBuilder, Mode mode, int nRows) {
        configureTableFormSelectionProviders(tableFormBuilder);
        tableFormBuilder.configPrefix(prefix).configNRows(nRows).configMode(mode);
        if(tableFormBuilder.getPropertyAccessors() == null) {
            tableFormBuilder.configReflectiveFields();
        }

        configureDetailLink(tableFormBuilder);
        configureSortLinks(tableFormBuilder);

        return tableFormBuilder;
    }

    /**
     * Creates and configures the {@link Form} used to display, edit and save a single object. As a side effect, assigns
     * that form to the field {@link #form}.
     * @param mode the {@link Mode} of the form.
     */
    protected void setupForm(Mode mode) {
        FormBuilder formBuilder = createFormBuilder();
        configureFormBuilder(formBuilder, mode);
        form = buildForm(formBuilder);
    }

    protected void configureFormSelectionProviders(FormBuilder formBuilder) {
        if(selectionProviderSupport == null) {
            return;
        }
        // setup option providers
        for (CrudSelectionProvider current : selectionProviderSupport.getCrudSelectionProviders()) {
            SelectionProvider selectionProvider = current.getSelectionProvider();
            if(selectionProvider == null) {
                continue;
            }
            String[] fieldNames = current.getFieldNames();
            if(object != null) {
                Object[] values = new Object[fieldNames.length];
                boolean valuesRead = true;
                for(int i = 0; i < fieldNames.length; i++) {
                    String fieldName = fieldNames[i];
                    try {
                        PropertyAccessor propertyAccessor = classAccessor.getProperty(fieldName);
                        values[i] = propertyAccessor.get(object);
                    } catch (Exception e) {
                        logger.error("Couldn't read property " + fieldName, e);
                        valuesRead = false;
                    }
                }
                if(valuesRead) {
                    selectionProvider.ensureActive(values);
                }
            }
            formBuilder.configSelectionProvider(selectionProvider, fieldNames);
        }
    }

    protected Form buildForm(FormBuilder formBuilder) {
        return formBuilder.build();
    }

    protected void disableBlobFields() {
        //Disable blob fields: we don't support them when bulk editing.
        for(FieldSet fieldSet : form) {
            for(FormElement element : fieldSet) {
                if(element instanceof FileBlobField) {
                    ((FileBlobField) element).setInsertable(false);
                    ((FileBlobField) element).setUpdatable(false);
                }
            }
        }
    }

    protected FormBuilder createFormBuilder() {
        return new FormBuilder(classAccessor);
    }

    /**
     * Configures the builder for the detail form (view, create, edit).
     * You can override this method to customize how the form is generated
     * (e.g. adding custom links on specific properties, hiding or showing properties
     * based on some runtime condition, etc.).
     * @param formBuilder the form builder.
     * @param mode the mode of the form.
     * @return the form builder.
     */
    protected FormBuilder configureFormBuilder(FormBuilder formBuilder, Mode mode) {
        formBuilder.configPrefix(prefix).configMode(mode).configNColumns(crudConfiguration.getColumns());
        configureFormSelectionProviders(formBuilder);
        return formBuilder;
    }

    //--------------------------------------------------------------------------
    // Blob management
    //--------------------------------------------------------------------------

    protected void refreshBlobDownloadHref() {
        for (FieldSet fieldSet : form) {
            for (Field field : fieldSet.fields()) {
                if (field instanceof AbstractBlobField) {
                    AbstractBlobField fileBlobField = (AbstractBlobField) field;
                    Blob blob = fileBlobField.getValue();
                    if (blob != null) {
                        String url = getBlobDownloadUrl(fileBlobField);
                        field.setHref(url);
                    }
                }
            }
        }
    }

    protected void refreshTableBlobDownloadHref() {
        Iterator<?> objIterator = objects.iterator();
        for (TableForm.Row row : tableForm.getRows()) {
            Iterator<Field> fieldIterator = row.iterator();
            Object obj = objIterator.next();
            String baseUrl = null;
            while (fieldIterator.hasNext()) {
                Field field = fieldIterator.next();
                if (field instanceof AbstractBlobField) {
                    if(baseUrl == null) {
                        OgnlTextFormat hrefFormat = getReadURLFormat();
                        baseUrl = hrefFormat.format(obj);
                    }

                    Blob blob = ((AbstractBlobField) field).getValue();
                    if(blob != null) {
                        Charset charset = Charset.forName(context.getRequest().getCharacterEncoding());
                        UrlBuilder urlBuilder = new UrlBuilder(charset, baseUrl, false)
                            .addParameter("downloadBlob", "")
                            .addParameter("propertyName", field.getPropertyAccessor().getName());
                        field.setHref(urlBuilder.toString());
                    }
                }
            }
        }
    }

    public String getBlobDownloadUrl(AbstractBlobField field) {
        Charset charset = Charset.forName(context.getRequest().getCharacterEncoding());
        UrlBuilder urlBuilder = new UrlBuilder(
                charset, Util.getAbsoluteUrl(context.getActionPath()), false)
                .addParameter("downloadBlob", "")
                .addParameter("propertyName", field.getPropertyAccessor().getName());
        return urlBuilder.toString();
    }

    @GET
    @Path(":blob/{propertyName}")
    public Response downloadBlob(@PathParam("propertyName") String propertyName) throws IOException {
        if(object == null) {
            return Response.status(Response.Status.BAD_REQUEST).
                    entity("Object can not be null (this method can only be called with /objectKey)").build();
        }
        setupForm(Mode.VIEW);
        form.readFromObject(object);
        BlobManager blobManager = getBlobManager();
        AbstractBlobField field = (AbstractBlobField) form.findFieldByPropertyName(propertyName);
        if(field == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Blob blob = field.getValue();
        if(blob == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if(blob.getInputStream() == null) {
            blobManager.loadMetadata(blob);
        }
        long contentLength = blob.getSize();
        String contentType = blob.getContentType();
        String fileName = blob.getFilename();
        long lastModified = blob.getCreateTimestamp().getMillis();
        HttpServletRequest request = context.getRequest();
        if(request.getHeader("If-Modified-Since") != null) {
            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if(ifModifiedSince >= lastModified) {
                return Response.status(Response.Status.NOT_MODIFIED).build();
            }
        }
        final InputStream inputStream;
        if(blob.getInputStream() == null) {
            inputStream = blobManager.openStream(blob);
        } else {
            inputStream = blob.getInputStream();
        }
        StreamingOutput streamingOutput = output -> {
            try {
                IOUtils.copyLarge(inputStream, output);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        };
        Response.ResponseBuilder responseBuilder = Response.ok(streamingOutput).
                type(contentType).
                lastModified(new Date(lastModified)).
                header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
        if(contentLength > 0) {
            responseBuilder.header(HttpHeaders.CONTENT_LENGTH, contentLength);
        }
        return responseBuilder.build();
    }

    @PUT
    @Path(":blob/{propertyName}")
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    @Guard(test = "isEditEnabled()", type = GuardType.VISIBLE)
    public Response uploadBlob(
            @PathParam("propertyName") String propertyName, @QueryParam("filename") String filename,
            InputStream inputStream)
            throws IOException {
        if(object == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Object can not be null (this method can only be called with /objectKey)").build();
        }
        setupForm(Mode.EDIT);
        form.readFromObject(object);
        AbstractBlobField field = (AbstractBlobField) form.findFieldByPropertyName(propertyName);
        if(field == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if(!field.isUpdatable()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Property not writable").build();
        }

        Blob blob = new Blob(field.generateNewCode());
        blob.setFilename(filename);
        blob.setSize(context.getRequest().getContentLength());
        blob.setContentType(context.getRequest().getContentType());
        blob.setCharacterEncoding(context.getRequest().getCharacterEncoding());
        blob.setCreateTimestamp(new DateTime());
        blob.setInputStream(inputStream);
        Blob oldBlob = field.getValue();
        field.setValue(blob);
        field.writeToObject(object);
        if(!field.isSaveBlobOnObject()) {
            BlobManager blobManager = getBlobManager();
            blobManager.save(blob);
            if(oldBlob != null) {
                try {
                    blobManager.delete(oldBlob);
                } catch (IOException e) {
                    logger.warn("Could not delete old blob (code: " + oldBlob.getCode() + ")", e);
                }
            }
        }
        commitTransaction();
        return Response.ok().build();
    }

    @DELETE
    @Path(":blob/{propertyName}")
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    public Response deleteBlob(
            @PathParam("propertyName") String propertyName)
            throws IOException {
        if(object == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Object can not be null (this method can only be called with /objectKey)").build();
        }
        setupForm(Mode.EDIT);
        form.readFromObject(object);
        AbstractBlobField field = (AbstractBlobField) form.findFieldByPropertyName(propertyName);
        if(!field.isUpdatable() || field.isRequired()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Property not writable").build();
        }
        Blob blob = field.getValue();
        field.setValue(null);
        field.writeToObject(object);
        if(!field.isSaveBlobOnObject() && blob != null) {
            BlobManager blobManager = getBlobManager();
            blobManager.delete(blob);
        }
        commitTransaction();
        return Response.ok().build();
    }

    protected BlobManager getBlobManager() {
        return blobManager;
    }

    public BlobManager getTemporaryBlobManager() {
        return temporaryBlobManager;
    }

    /**
     * Removes all the file blobs associated with the object from the file system.
     * @param object the persistent object.
     */
    protected void deleteBlobs(T object) {
        List<Blob> blobs = getBlobsFromObject(object);
        for(Blob blob : blobs) {
            try {
                blobManager.delete(blob);
            } catch (IOException e) {
                logger.warn("Could not delete blob: " + blob.getCode(), e);
            }
        }
    }

    protected List<Blob> getBlobsFromObject(T object) {
        List<Blob> blobs = new ArrayList<Blob>();
        for(PropertyAccessor property : classAccessor.getProperties()) {
            if(property.getAnnotation(FileBlob.class) != null) {
                String code = (String) property.get(object);
                if(!StringUtils.isBlank(code)) {
                    blobs.add(new Blob(code));
                }
            }
        }
        return blobs;
    }

    protected List<Blob> getBlobsFromForm() {
        List<Blob> blobs = new ArrayList<Blob>();
        for(FileBlobField blobField : getBlobFields()) {
            if(blobField.getValue() != null) {
                blobs.add(blobField.getValue());
            }
        }
        return blobs;
    }

    protected List<FileBlobField> getBlobFields() {
        List<FileBlobField> blobFields = new ArrayList<FileBlobField>();
        for(FieldSet fieldSet : form) {
            for(FormElement field : fieldSet) {
                if(field instanceof FileBlobField) {
                    blobFields.add((FileBlobField) field);
                }
            }
        }
        return blobFields;
    }

    //**************************************************************************
    // Configuration
    //**************************************************************************

/*    @Button(list = "pageHeaderButtons", titleKey = "configure", order = 1, icon = Button.ICON_WRENCH)
    @RequiresPermissions(level = AccessLevel.DEVELOP)
    public Resolution configure() {
        prepareConfigurationForms();

        crudConfigurationForm.readFromObject(crudConfiguration);
        if(propertyEdits != null) {
            propertiesTableForm.readFromObject(propertyEdits);
        }

        if(selectionProviderEdits != null) {
            selectionProvidersForm.readFromObject(selectionProviderEdits);
        }

        return getConfigurationView();
    }
*/
    @Override
    protected void prepareConfigurationForms() {
        super.prepareConfigurationForms();

        setupPropertyEdits();

        if(propertyEdits != null) {
            TableFormBuilder tableFormBuilder =
                    new TableFormBuilder(CrudPropertyEdit.class)
                        .configNRows(propertyEdits.length);
            propertiesTableForm = tableFormBuilder.build();
            propertiesTableForm.setCondensed(true);
        }

        if(selectionProviderSupport != null) {
            Map<List<String>, Collection<String>> selectionProviderNames =
                    selectionProviderSupport.getAvailableSelectionProviderNames();
            if(!selectionProviderNames.isEmpty()) {
                setupSelectionProviderEdits();
                setupSelectionProvidersForm(selectionProviderNames);
            }
        }

        buildConfigurationForm();
    }

    protected void buildConfigurationForm() {
        FormBuilder formBuilder = new FormBuilder(PageActionLogic.getConfigurationClass(getClass()));
        setupConfigurationForm(formBuilder);
        crudConfigurationForm = formBuilder.build();
    }

    protected void setupConfigurationForm(FormBuilder formBuilder) {
        DefaultSelectionProvider nColumnsSelectionProvider = new DefaultSelectionProvider("columns");
        nColumnsSelectionProvider.setDisplayMode(DisplayMode.DROPDOWN);
        nColumnsSelectionProvider.appendRow(1, "1 column", true);
        nColumnsSelectionProvider.appendRow(2, "2 columns", true);
        nColumnsSelectionProvider.appendRow(3, "3 columns", true);
        nColumnsSelectionProvider.appendRow(4, "4 columns", true);
        nColumnsSelectionProvider.appendRow(6, "6 columns", true);
        formBuilder.configSelectionProvider(nColumnsSelectionProvider, "columns");
    }

    protected void setupSelectionProvidersForm(Map<List<String>, Collection<String>> selectionProviderNames) {
        TableFormBuilder tableFormBuilder = new TableFormBuilder(CrudSelectionProviderEdit.class);
        tableFormBuilder.configNRows(selectionProviderNames.size());
        for(int i = 0; i < selectionProviderEdits.length; i++) {
            Collection<String> availableProviders =
                    selectionProviderNames.get(Arrays.asList(selectionProviderEdits[i].fieldNames));
            if(availableProviders == null || availableProviders.size() == 0) {
                continue;
            }
            DefaultSelectionProvider selectionProvider =
                    new DefaultSelectionProvider(selectionProviderEdits[i].columns);
            selectionProvider.appendRow(null, "None", true);
            for(String spName : availableProviders) {
                selectionProvider.appendRow(spName, spName, true);
            }
            tableFormBuilder.configSelectionProvider(i, selectionProvider, "selectionProvider");
        }
        selectionProvidersForm = tableFormBuilder.build();
        selectionProvidersForm.setCondensed(true);
    }

    protected void setupPropertyEdits() {
        if(classAccessor == null) {
            return;
        }
        PropertyAccessor[] propertyAccessors = classAccessor.getProperties();
        propertyEdits = new CrudPropertyEdit[propertyAccessors.length];
        for (int i = 0; i < propertyAccessors.length; i++) {
            CrudPropertyEdit edit = new CrudPropertyEdit();
            PropertyAccessor propertyAccessor = propertyAccessors[i];
            edit.name = propertyAccessor.getName();
            com.manydesigns.elements.annotations.Label labelAnn =
                    propertyAccessor.getAnnotation(com.manydesigns.elements.annotations.Label.class);
            edit.label = labelAnn != null ? labelAnn.value() : null;
            Enabled enabledAnn = propertyAccessor.getAnnotation(Enabled.class);
            edit.enabled = enabledAnn != null && enabledAnn.value();
            InSummary inSummaryAnn = propertyAccessor.getAnnotation(InSummary.class);
            edit.inSummary = inSummaryAnn != null && inSummaryAnn.value();
            Insertable insertableAnn = propertyAccessor.getAnnotation(Insertable.class);
            edit.insertable = insertableAnn != null && insertableAnn.value();
            Updatable updatableAnn = propertyAccessor.getAnnotation(Updatable.class);
            edit.updatable = updatableAnn != null && updatableAnn.value();
            Searchable searchableAnn = propertyAccessor.getAnnotation(Searchable.class);
            edit.searchable = searchableAnn != null && searchableAnn.value();
            propertyEdits[i] = edit;
        }
    }

    protected void setupSelectionProviderEdits() {
        Map<List<String>, Collection<String>> availableSelectionProviders =
                selectionProviderSupport.getAvailableSelectionProviderNames();
        selectionProviderEdits = new CrudSelectionProviderEdit[availableSelectionProviders.size()];
        int i = 0;
        for(List<String> key : availableSelectionProviders.keySet()) {
            selectionProviderEdits[i] = new CrudSelectionProviderEdit();
            String[] fieldNames = key.toArray(new String[key.size()]);
            selectionProviderEdits[i].fieldNames = fieldNames;
            selectionProviderEdits[i].columns = StringUtils.join(fieldNames, ", ");
            for(CrudSelectionProvider cp : selectionProviderSupport.getCrudSelectionProviders()) {
                if(Arrays.equals(cp.fieldNames, fieldNames)) {
                    SelectionProvider selectionProvider = cp.getSelectionProvider();
                    if(selectionProvider != null) {
                        selectionProviderEdits[i].selectionProvider = selectionProvider.getName();
                        selectionProviderEdits[i].displayMode = selectionProvider.getDisplayMode();
                        selectionProviderEdits[i].searchDisplayMode = selectionProvider.getSearchDisplayMode();
                        selectionProviderEdits[i].createNewHref = selectionProvider.getCreateNewValueHref();
                        selectionProviderEdits[i].createNewText = selectionProvider.getCreateNewValueText();
                    } else {
                        selectionProviderEdits[i].selectionProvider = null;
                        selectionProviderEdits[i].displayMode = DisplayMode.DROPDOWN;
                        selectionProviderEdits[i].searchDisplayMode = SearchDisplayMode.DROPDOWN;
                    }
                }
            }
            i++;
        }
    }

    /*@Button(list = "configuration", key = "update.configuration", order = 1, type = Button.TYPE_PRIMARY)
    @RequiresPermissions(level = AccessLevel.DEVELOP)
    public Resolution updateConfiguration() {
        prepareConfigurationForms();

        crudConfigurationForm.readFromObject(crudConfiguration);

        readPageConfigurationFromRequest();

        crudConfigurationForm.readFromRequest(context.getRequest());

        boolean valid = crudConfigurationForm.validate();
        valid = validatePageConfiguration() && valid;

        if(propertiesTableForm != null) {
            propertiesTableForm.readFromObject(propertyEdits);
            propertiesTableForm.readFromRequest(context.getRequest());
            valid = propertiesTableForm.validate() && valid;
        }

        if(selectionProvidersForm != null) {
            selectionProvidersForm.readFromRequest(context.getRequest());
            valid = selectionProvidersForm.validate() && valid;
        }

        if (valid) {
            updatePageConfiguration();
            if(crudConfiguration == null) {
                crudConfiguration = new CrudConfiguration();
            }
            crudConfigurationForm.writeToObject(crudConfiguration);

            if(propertiesTableForm != null) {
                updateProperties();
            }

            if(selectionProviderSupport != null &&
               !selectionProviderSupport.getAvailableSelectionProviderNames().isEmpty()) {
                updateSelectionProviders();
            }

            saveConfiguration(crudConfiguration);

            SessionMessages.addInfoMessage(ElementsThreadLocals.getText("configuration.updated.successfully"));
            return cancel();
        } else {
            SessionMessages.addErrorMessage(ElementsThreadLocals.getText("the.configuration.could.not.be.saved"));
            return getConfigurationView();
        }
    }

    protected void updateSelectionProviders() {
        selectionProvidersForm.writeToObject(selectionProviderEdits);
        selectionProviderSupport.clearSelectionProviders();
        for(CrudSelectionProviderEdit sp : selectionProviderEdits) {
            List<String> key = Arrays.asList(sp.fieldNames);
            if(sp.selectionProvider == null) {
                selectionProviderSupport.disableSelectionProvider(key);
            } else {
                selectionProviderSupport.configureSelectionProvider(
                        key, sp.selectionProvider, sp.displayMode, sp.searchDisplayMode,
                        StringUtils.trimToNull(sp.createNewHref), sp.createNewText);
            }
        }
    }

    protected void updateProperties() {
        propertiesTableForm.writeToObject(propertyEdits);

        List<CrudProperty> newProperties = new ArrayList<CrudProperty>();
        List<CrudProperty> properties = crudConfiguration.getProperties();
        for (CrudPropertyEdit edit : propertyEdits) {
            CrudProperty crudProperty = findProperty(edit.name, properties);
            if(crudProperty == null) {
                crudProperty = new CrudProperty();
                properties.add(crudProperty);
            }

            crudProperty.setName(edit.name);
            crudProperty.setLabel(edit.label);
            crudProperty.setInSummary(edit.inSummary);
            crudProperty.setSearchable(edit.searchable);
            crudProperty.setEnabled(edit.enabled);
            crudProperty.setInsertable(edit.insertable);
            crudProperty.setUpdatable(edit.updatable);

            newProperties.add(crudProperty);
        }
        Iterator<CrudProperty> propertyIterator = properties.iterator();
        while (propertyIterator.hasNext()) {
            CrudProperty property = propertyIterator.next();
            if(!(property instanceof VirtualCrudProperty) && !newProperties.contains(property)) {
                propertyIterator.remove();
            }
        }
    }*/

    public boolean isRequiredFieldsPresent() {
        return form.isRequiredFieldsPresent();
    }

    //**************************************************************************
    // Selection providers
    //**************************************************************************

    public Response jsonSelectFieldOptions() {
        return jsonOptions(relName, prefix, labelSearch, true);
    }

    public Response jsonSelectFieldSearchOptions() {
        return jsonOptions(relName, searchPrefix, labelSearch, true);
    }

    public Response jsonAutocompleteOptions() {
        return jsonOptions(relName, prefix, labelSearch, false);
    }

    public Response jsonAutocompleteSearchOptions() {
        return jsonOptions(relName, searchPrefix, labelSearch, false);
    }

    /**
     * Returns values to update a single select or autocomplete field, in JSON form.
     * See {@link #jsonOptions(String, int, String, String, boolean)}.
     * @param selectionProviderName name of the selection provider. See {@link #selectionProviders()}.
     * @param labelSearch for autocomplete fields, the text entered by the user.
     * @param prefix form prefix, to read values from the request.
     * @param includeSelectPrompt controls if the first option is a label with no value indicating
     * what field is being selected. For combo boxes you would generally pass true as the value of
     * this parameter; for autocomplete fields, you would likely pass false.
     * @return a Response with the JSON.
     */
    @GET
    @Path(":selectionProvider/{selectionProviderName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response jsonOptions(
            @PathParam("selectionProviderName") String selectionProviderName,
            @QueryParam("labelSearch") String labelSearch,
            @QueryParam("prefix") String prefix,
            @QueryParam("includeSelectPrompt") boolean includeSelectPrompt) {
        return jsonOptions(selectionProviderName, selectionProviderIndex, labelSearch, prefix, includeSelectPrompt);
    }
    
    /**
     * Returns values to update multiple related select fields or a single autocomplete text field, in JSON form.
     * @param selectionProviderName name of the selection provider. See {@link #selectionProviders()}.
     * @param selectionProviderIndex index of the selection field (in case of multiple-valued selection providers,
     *                               otherwise it is always 0 and you can use
     *                               {@link #jsonOptions(String, String, String, boolean)}).
     * @param labelSearch for autocomplete fields, the text entered by the user.
     * @param prefix form prefix, to read values from the request.
     * @param includeSelectPrompt controls if the first option is a label with no value indicating
     * what field is being selected. For combo boxes you would generally pass true as the value of
     * this parameter; for autocomplete fields, you would likely pass false.
     * @return a Response with the JSON.
     */
    @GET
    @Path(":selectionProvider/{selectionProviderName}/{selectionProviderIndex : (\\d+)}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response jsonOptions(
            @PathParam("selectionProviderName") String selectionProviderName,
            @PathParam("selectionProviderIndex") int selectionProviderIndex,
            @QueryParam("labelSearch") String labelSearch,
            @QueryParam("prefix") String prefix,
            @QueryParam("includeSelectPrompt") boolean includeSelectPrompt) {
        CrudSelectionProvider crudSelectionProvider = null;
        for (CrudSelectionProvider current : selectionProviderSupport.getCrudSelectionProviders()) {
            SelectionProvider selectionProvider = current.getSelectionProvider();
            if (selectionProvider.getName().equals(selectionProviderName)) {
                crudSelectionProvider = current;
                break;
            }
        }
        if (crudSelectionProvider == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        SelectionProvider selectionProvider = crudSelectionProvider.getSelectionProvider();
        String[] fieldNames = crudSelectionProvider.getFieldNames();

        Form form = buildForm(createFormBuilder()
                .configFields(fieldNames)
                .configSelectionProvider(selectionProvider, fieldNames)
                .configPrefix(prefix)
                .configMode(Mode.EDIT));

        FieldSet fieldSet = form.get(0); //Guaranteed to be the only one per the code above
        //Ensure the value is actually read from the request
        for(Field field : fieldSet.fields()) {
            field.setUpdatable(true);
        }
        form.readFromRequest(context.getRequest());
        
        //The form only contains fields from the selection provider, so the index matches that of the field
        if(selectionProviderIndex < 0 || selectionProviderIndex >= fieldSet.size()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid index").build();
        }
        SelectField targetField = (SelectField) fieldSet.get(selectionProviderIndex);
        targetField.setLabelSearch(labelSearch);

        String text = targetField.jsonSelectFieldOptions(includeSelectPrompt);
        logger.debug("jsonOptions: {}", text);
        return Response.ok(text, MimeTypes.APPLICATION_JSON_UTF8).build();
    }

    @GET
    @Path(":selectionProviders")
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressWarnings("unchecked")
    public List selectionProviders() {
        List result = new ArrayList();
        // setup option providers
        for (CrudSelectionProvider current : selectionProviderSupport.getCrudSelectionProviders()) {
            SelectionProvider selectionProvider = current.getSelectionProvider();
            if(selectionProvider == null) {
                continue;
            }
            String[] fieldNames = current.getFieldNames();
            Map description = new HashMap();
            description.put("name", selectionProvider.getName());
            description.put("fieldNames", Arrays.asList(fieldNames));
            description.put("displayMode", selectionProvider.getDisplayMode());
            description.put("searchDisplayMode", selectionProvider.getSearchDisplayMode());
            result.add(description);
        }
        return result;
    }

    //--------------------------------------------------------------------------
    // Utilities
    //--------------------------------------------------------------------------

    protected String getUrlEncoding() {
        return portofinoConfiguration.getString(
                PortofinoProperties.URL_ENCODING, PortofinoProperties.URL_ENCODING_DEFAULT);
    }

    /**
     * Searches in a list of properties for a property with a given name.
     * @param name the name of the properties.
     * @param properties the list to search.
     * @return the property with the given name, or null if it couldn't be found.
     */
    protected CrudProperty findProperty(String name, List<CrudProperty> properties) {
        for(CrudProperty p : properties) {
            if(p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Encodes the exploded object indentifier to include it in a URL.
     * @param pk the object identifier as a String array.
     * @return the string to append to the URL.
     */
    protected String getPkForUrl(String[] pk) {
        String encoding = getUrlEncoding();
        try {
            return pkHelper.getPkStringForUrl(pk, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    /**
     * Returns an OGNL expression that, when evaluated against a persistent object, produces a
     * URL path suitable to be used as a link to that object.
     * @return the read link expression.
     */
    protected String getReadLinkExpression() {
        String actionPath = context.getActionPath();
        StringBuilder sb = new StringBuilder(actionPath);
        if(!actionPath.endsWith("/")) {
            sb.append("/");
        }
        boolean first = true;

        for (PropertyAccessor property : classAccessor.getKeyProperties()) {
            if (first) {
                first = false;
            } else {
                sb.append("/");
            }
            sb.append("%{");
            sb.append(property.getName());
            sb.append("}");
        }
        appendSearchStringParamIfNecessary(sb);
        return sb.toString();
    }

    /**
     * Computes an OgnlTextFormat from the result of getReadLinkExpression(), with the correct URL encoding.
     * @return the OgnlTextFormat.
     */
    protected OgnlTextFormat getReadURLFormat() {
        String readLinkExpression = getReadLinkExpression();
        OgnlTextFormat hrefFormat = OgnlTextFormat.create(readLinkExpression);
        hrefFormat.setUrl(true);
        String encoding = getUrlEncoding();
        hrefFormat.setEncoding(encoding);
        return hrefFormat;
    }

    /**
     * If a search has been executed, appends a URL-encoded String representation of the search criteria
     * to the given string, as a GET parameter.
     * @param s the base string.
     * @return the base string with the search criteria appended
     */
    protected String appendSearchStringParamIfNecessary(String s) {
        return appendSearchStringParamIfNecessary(new StringBuilder(s)).toString();
    }

    /**
     * If a search has been executed, appends a URL-encoded String representation of the search criteria
     * to the given StringBuilder, as a GET parameter. The StringBuilder's contents are modified.
     * @param sb the base string.
     * @return sb.
     */
    protected StringBuilder appendSearchStringParamIfNecessary(StringBuilder sb) {
        String searchStringParam = getEncodedSearchStringParam();
        if(searchStringParam != null) {
            if(sb.indexOf("?") == -1) {
                sb.append('?');
            } else {
                sb.append('&');
            }
            sb.append(searchStringParam);
        }
        return sb;
    }

    /**
     * Encodes the current search string (a representation of the current search criteria as a series of GET
     * parameters) to an URL-encoded GET parameter.
     * @return the encoded search string.
     */
    protected String getEncodedSearchStringParam() {
        if(StringUtils.isBlank(searchString)) {
            return null;
        }
        String encodedSearchString = "searchString=";
        try {
            String encoding = getUrlEncoding();
            String encoded = URLEncoder.encode(searchString, encoding);
            if(searchString.equals(URLDecoder.decode(encoded, encoding))) {
                encodedSearchString += encoded;
            } else {
                logger.warn("Could not encode search string \"" + StringEscapeUtils.escapeJava(searchString) +
                            "\" with encoding " + encoding);
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
        return encodedSearchString;
    }

    @Deprecated
    protected void fieldsToJson(JSONStringer js, Collection<Field> fields) throws JSONException {
        FormUtil.fieldsToJson(js, fields);
    }

    @Deprecated
    protected List<Field> collectVisibleFields(Form form, List<Field> fields) {
        return FormUtil.collectVisibleFields(form, fields);
    }

    @Deprecated
    protected List<Field> collectVisibleFields(FieldSet fieldSet, List<Field> fields) {
        return FormUtil.collectVisibleFields(fieldSet, fields);
    }

    //--------------------------------------------------------------------------
    // REST
    //--------------------------------------------------------------------------

    /**
     * Handles search and detail via REST. See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @param searchString the search string
     * @param firstResult pagination: the index of the first result returned by the search
     * @param maxResults pagination: the maximum number of results returned by the search
     * @since 4.2
     * @return search results (/) or single object (/pk) as JSON
     */
    @GET
    @Produces(MimeTypes.APPLICATION_JSON_UTF8)
    public Response getAsJson(
            @QueryParam("searchString") String searchString,
            @QueryParam("firstResult") Integer firstResult, @QueryParam("maxResults") Integer maxResults,
            @QueryParam("sortProperty") String sortProperty, @QueryParam("sortDirection") String sortDirection,
            @QueryParam("forEdit") boolean forEdit, @QueryParam("newObject") boolean newObject) {
        if(newObject) {
            return jsonCreateData();
        }
        if(object == null) {
            this.searchString = searchString;
            this.firstResult = firstResult;
            this.maxResults = maxResults;
            this.sortProperty = sortProperty;
            this.sortDirection = sortDirection;
            return jsonSearchData();
        } else if(forEdit) {
            return jsonEditData();
        } else {
            return jsonReadData();
        }
    }

    /**
     * Handles object creation via REST. See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @param jsonObject the object (in serialized JSON form)
     * @since 4.2
     * @return the created object as JSON (in a JAX-RS Response).
     * @throws Exception only to make the compiler happy. Nothing should be thrown in normal operation. If this method throws, it is probably a bug.
     */
    @POST
    @RequiresPermissions(permissions = PERMISSION_CREATE)
    @Produces(MimeTypes.APPLICATION_JSON_UTF8)
    @Consumes(MimeTypes.APPLICATION_JSON_UTF8)
    public Response httpPostJson(String jsonObject) throws Exception {
        if(object != null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("update not supported, PUT to /objectKey instead").build();
        }
        preCreate();
        FormUtil.readFromJson(form, new JSONObject(jsonObject));
        if (form.validate()) {
            writeFormToObject();
            if(createValidate(object)) {
                try {
                    doSave(object);
                    createPostProcess(object);
                    commitTransaction();
                } catch (Throwable e) {
                    String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                    logger.warn(rootCauseMessage, e);
                    return Response.serverError().entity(e).build();
                }
                return objectCreated();
            } else {
                return Response.serverError().entity(form).build();
            }
        } else {
            return Response.serverError().entity(form).build();
        }
    }

    /**
     * Handles object creation with attachments via REST. See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @since 4.2.1
     * @return the created object as JSON (in a JAX-RS Response).
     * @throws Exception only to make the compiler happy. Nothing should be thrown in normal operation. If this method throws, it is probably a bug. 
     */
    @POST
    @RequiresPermissions(permissions = PERMISSION_CREATE)
    @Produces(MimeTypes.APPLICATION_JSON_UTF8)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response httpPostMultipart() throws Exception {
        if(object != null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                    "update not supported, PUT to /objectKey instead").build();
        }
        preCreate();
        form.readFromRequest(context.getRequest());
        if (form.validate()) {
            writeFormToObject();
            if(createValidate(object)) {
                try {
                    doSave(object);
                    createPostProcess(object);
                    BlobUtils.saveBlobs(form, getBlobManager());
                    commitTransaction();
                } catch (Throwable e) {
                    String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                    logger.warn(rootCauseMessage, e);
                    return Response.serverError().entity(e).build();
                }
                return objectCreated();
            }
        }
        return Response.serverError().entity(form).build();
    }

    protected Response objectCreated() throws URISyntaxException {
        form.readFromObject(object); //Re-read so that the full object is returned
        OgnlTextFormat textFormat = getReadURLFormat();
        return Response.status(Response.Status.CREATED).
                entity(form).
                location(new URI(textFormat.format(object))).
                build();
    }

    /**
     * Handles object update via REST; either a single object or several ones in bulk.
     * Note: this doesn't support blobs, see {@link #httpPutMultipart()} and
     * {@link #uploadBlob(String, String, InputStream)}.
     * See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @param jsonObject the object (in serialized JSON form)
     * @param ids the list of object id's (keys) to save if this is a bulk operation.
     * @since 4.2
     * @return the updated object as JSON (in a JAX-RS Response).
     */
    @PUT
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    @Produces(MimeTypes.APPLICATION_JSON_UTF8)
    @Consumes(MimeTypes.APPLICATION_JSON_UTF8)
    @Guard(test = "isEditEnabled() && (getObject() != null || isBulkOperationsEnabled())", type = GuardType.VISIBLE)
    public Response httpPutJson(@QueryParam("id") List<String> ids, String jsonObject) {
        if(object == null) {
            return bulkUpdate(jsonObject, ids);
        }
        if(ids != null && !ids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                    "You must either PUT a single object (/key) or PUT multiple objects (?ids=...), not both.").build();
        }
        return update(jsonObject);
    }

    protected Response update(String jsonObject) {
        preEdit();
        FormUtil.readFromJson(form, new JSONObject(jsonObject));
        if (form.validate()) {
            writeFormToObject();
            if(editValidate(object)) {
                try {
                    doUpdate(object);
                    editPostProcess(object);
                    commitTransaction();
                } catch (Throwable e) {
                    String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                    logger.warn(rootCauseMessage, e);
                    return Response.serverError().entity(e).build();
                }
                form.readFromObject(object); //Re-read so that the full object is returned
                return Response.ok(form).build();
            } else {
                return Response.serverError().entity(form).build();
            }
        } else {
            return Response.serverError().entity(form).build();
        }
    }

    /**
     * Handles the update of multiple objects via REST.
     * Note: this doesn't support blobs, see {@link #httpPutMultipart()} and
     * {@link #uploadBlob(String, String, InputStream)}.
     * See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @param jsonObject the object (in serialized JSON form)
     * @since 4.2.4-SNAPSHOT
     * @return the updated object as JSON (in a JAX-RS Response).
     */
    protected Response bulkUpdate(String jsonObject, List<String> ids) {
        List<String> idsNotUpdated = new ArrayList<>();
        setupForm(Mode.BULK_EDIT);
        disableBlobFields();
        FormUtil.readFromJson(form, new JSONObject(jsonObject));
        if (form.validate()) {
            for (String id : ids) {
                loadObject(id.split("/"));
                editSetup(object);
                writeFormToObject();
                if(editValidate(object)) {
                    doUpdate(object);
                    editPostProcess(object);
                } else {
                    idsNotUpdated.add(id);
                }
            }
            try {
                commitTransaction();
            } catch (Throwable e) {
                String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                logger.warn(rootCauseMessage, e);
                return Response.serverError().entity(e).build();
            }
            return Response.ok(idsNotUpdated).build();
        } else {
            return Response.serverError().entity(form).build();
        }
    }

    /**
     * Handles object update with attachments via REST.
     * See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @since 4.2
     * @return the updated object as JSON (in a JAX-RS Response).
     */
    @PUT
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    @Produces(MimeTypes.APPLICATION_JSON_UTF8)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Guard(test = "isEditEnabled()", type = GuardType.VISIBLE)
    public Response httpPutMultipart() throws Throwable {
        if(object == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("create not supported, POST to / instead").build();
        }
        preEdit();
        List<Blob> blobsBefore = getBlobsFromForm();
        form.readFromRequest(context.getRequest());
        if (form.validate()) {
            writeFormToObject();
            if(editValidate(object)) {
                try {
                    doUpdate(object);
                    editPostProcess(object);
                    commitTransaction();
                } catch (Throwable e) {
                    String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                    logger.warn(rootCauseMessage, e);
                    return Response.serverError().entity(e).build();
                }
                boolean blobSaved = true;
                try {
                    List<Blob> blobsAfter = getBlobsFromForm();
                    deleteOldBlobs(blobsBefore, blobsAfter);
                    persistNewBlobs(blobsBefore, blobsAfter);
                } catch (IOException e) {
                    logger.warn("Could not save blobs", e);
                    blobSaved = false;
                }
                form.readFromObject(object); //Re-read so that the full object is returned
                Response.ResponseBuilder responseBuilder = Response.ok(form);
                if(!blobSaved) {
                    responseBuilder.header("X-Portofino-Blob-Warning", "Not all blobs were saved. See application logs.");
                }
                return responseBuilder.build();
            } else {
                return Response.serverError().entity(form).build();
            }
        } else {
            return Response.serverError().entity(form).build();
        }
    }

    /**
     * Handles object deletion via REST.
     * @param ids the list of object id's (keys) to delete if this is a bulk deletion.
     * See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @since 4.2
     */
    @DELETE
    @RequiresPermissions(permissions = PERMISSION_DELETE)
    @Guard(test = "isDeleteEnabled() && (getObject() != null || isBulkOperationsEnabled())", type = GuardType.VISIBLE)
    public int httpDelete(@QueryParam("id") List<String> ids) throws Exception {
        if(object == null) {
            return bulkDelete(ids);
        }
        if(ids != null && !ids.isEmpty()) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(
                    "DELETE requires either a /objectKey path parameter or a list of id query parameters").build());
        }
        return delete(object);
    }

    protected int delete(T object) {
        if(deleteValidate(object)) {
            try {
                doDelete(object);
                deletePostProcess(object);
                commitTransaction();
                deleteBlobs(object);
                return 1;
            } catch (Exception e) {
                String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                logger.warn(rootCauseMessage, e);
                throw e;
            }
        } else {
            return 0;
        }
    }

    protected int bulkDelete(List<String> ids) throws Exception {
        List<T> objects = new ArrayList<T>(ids.size());
        int deleted = 0;
        for (String current : ids) {
            String[] pkArr = current.split("/");
            Serializable pkObject = pkHelper.getPrimaryKey(pkArr);
            T obj = loadObjectByPrimaryKey(pkObject);
            if(obj != null && deleteValidate(obj)) {
                doDelete(obj);
                deletePostProcess(obj);
                objects.add(obj);
                deleted++;
            }
        }
        commitTransaction();
        for(T obj : objects) {
            deleteBlobs(obj);
        }
        return deleted;
    }

    /**
     * Returns a description of this CRUD's ClassAccessor.
     * See <a href="http://portofino.manydesigns.com/en/docs/reference/page-types/crud/rest">the CRUD action REST API documentation.</a>
     * @since 4.2
     * @return the class accessor as JSON.
     */
    @Path(":classAccessor")
    @GET
    @Produces(MimeTypes.APPLICATION_JSON_UTF8)
    public String describeClassAccessor() {
        JSONStringer jsonStringer = new JSONStringer();
        ReflectionUtil.classAccessorToJson(getClassAccessor(), jsonStringer);
        return jsonStringer.toString();
    }

    //--------------------------------------------------------------------------
    // Accessors
    //--------------------------------------------------------------------------

    public String getReadTitle() {
        String title = crudConfiguration.getReadTitle();
        if(StringUtils.isEmpty(title)) {
            return ShortNameUtils.getName(getClassAccessor(), object);
        } else {
            OgnlTextFormat textFormat = OgnlTextFormat.create(title);
            return textFormat.format(this);
        }
    }

    public String getSearchTitle() {
        String title = crudConfiguration.getSearchTitle();
        if(StringUtils.isBlank(title)) {
            title = getPage().getTitle();
        }
        OgnlTextFormat textFormat = OgnlTextFormat.create(StringUtils.defaultString(title));
        return textFormat.format(this);
    }

    public String getEditTitle() {
        String title = crudConfiguration.getEditTitle();
        if(StringUtils.isEmpty(title)) {
            return ShortNameUtils.getName(getClassAccessor(), object);
        } else {
            OgnlTextFormat textFormat = OgnlTextFormat.create(StringUtils.defaultString(title));
            return textFormat.format(this);
        }
    }

    public String getCreateTitle() {
        String title = crudConfiguration.getCreateTitle();
        if(StringUtils.isBlank(title)) {
            title = getPage().getTitle();
        }
        OgnlTextFormat textFormat = OgnlTextFormat.create(StringUtils.defaultString(title));
        return textFormat.format(this);
    }

    public CrudConfiguration getCrudConfiguration() {
        return crudConfiguration;
    }

    public void setCrudConfiguration(CrudConfiguration crudConfiguration) {
        this.crudConfiguration = crudConfiguration;
    }

    public ClassAccessor getClassAccessor() {
        return classAccessor;
    }

    public void setClassAccessor(ClassAccessor classAccessor) {
        this.classAccessor = classAccessor;
    }

    public PkHelper getPkHelper() {
        return pkHelper;
    }

    public void setPkHelper(PkHelper pkHelper) {
        this.pkHelper = pkHelper;
    }

    public List<CrudSelectionProvider> getCrudSelectionProviders() {
        return selectionProviderSupport.getCrudSelectionProviders();
    }

    public String[] getSelection() {
        return selection;
    }

    public void setSelection(String[] selection) {
        this.selection = selection;
    }

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public String getSuccessReturnUrl() {
        return successReturnUrl;
    }

    public void setSuccessReturnUrl(String successReturnUrl) {
        this.successReturnUrl = successReturnUrl;
    }

    public SearchForm getSearchForm() {
        return searchForm;
    }

    public void setSearchForm(SearchForm searchForm) {
        this.searchForm = searchForm;
    }

    public List<? extends T> getObjects() {
        return objects;
    }

    public T getObject() {
        return object;
    }

    public boolean isMultipartRequest() {
        return form != null && form.isMultipartRequest();
    }

    @Deprecated
    public List<TextField> getEditableRichTextFields() {
        return FormUtil.collectEditableRichTextFields(form);
    }

    public boolean isFormWithRichTextFields() {
        return !FormUtil.collectEditableRichTextFields(form).isEmpty();
    }

    public Form getCrudConfigurationForm() {
        return crudConfigurationForm;
    }

    public void setCrudConfigurationForm(Form crudConfigurationForm) {
        this.crudConfigurationForm = crudConfigurationForm;
    }

    public TableForm getPropertiesTableForm() {
        return propertiesTableForm;
    }

    public Form getForm() {
        return form;
    }

    public void setForm(Form form) {
        this.form = form;
    }

    public TableForm getTableForm() {
        return tableForm;
    }

    public void setTableForm(TableForm tableForm) {
        this.tableForm = tableForm;
    }

    public TableForm getSelectionProvidersForm() {
        return selectionProvidersForm;
    }

    public Integer getFirstResult() {
        return firstResult;
    }

    public void setFirstResult(Integer firstResult) {
        this.firstResult = firstResult;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public String getSortProperty() {
        return sortProperty;
    }

    public void setSortProperty(String sortProperty) {
        this.sortProperty = sortProperty;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public boolean isSearchVisible() {
        //If embedded, search is always closed by default
        return searchVisible && !PageActionLogic.isEmbedded(this);
    }

    public void setSearchVisible(boolean searchVisible) {
        this.searchVisible = searchVisible;
    }

    public String getRelName() {
        return relName;
    }

    public void setRelName(String relName) {
        this.relName = relName;
    }

    public int getSelectionProviderIndex() {
        return selectionProviderIndex;
    }

    public void setSelectionProviderIndex(int selectionProviderIndex) {
        this.selectionProviderIndex = selectionProviderIndex;
    }

    public String getLabelSearch() {
        return labelSearch;
    }

    public void setLabelSearch(String labelSearch) {
        this.labelSearch = labelSearch;
    }

    public boolean isPopup() {
        return !StringUtils.isEmpty(popupCloseCallback);
    }

    public String getPopupCloseCallback() {
        return popupCloseCallback;
    }

    public void setPopupCloseCallback(String popupCloseCallback) {
        this.popupCloseCallback = popupCloseCallback;
    }

    public ResultSetNavigation getResultSetNavigation() {
        return resultSetNavigation;
    }

    public void setResultSetNavigation(ResultSetNavigation resultSetNavigation) {
        this.resultSetNavigation = resultSetNavigation;
    }

}
