/*
 * Copyright (C) 2005-2010 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * There are special exceptions to the terms and conditions of the GPL
 * as it is applied to this software. View the full text of the
 * exception in file OPEN-SOURCE-LICENSE.txt in the directory of this
 * software distribution.
 *
 * This program is distributed WITHOUT ANY WARRANTY; and without the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see http://www.gnu.org/licenses/gpl.txt
 * or write to:
 * Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307  USA
 *
 */

package com.manydesigns.portofino.base.context;

import com.manydesigns.portofino.base.database.HibernateConfig;
import com.manydesigns.portofino.base.model.*;
import com.manydesigns.elements.logging.LogUtil;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
*/
public class MDContext {
    public static final String copyright =
            "Copyright (c) 2005-2010, ManyDesigns srl";

    //--------------------------------------------------------------------------
    // Fields
    //--------------------------------------------------------------------------

    protected DataModel dataModel;
    protected Map<String, SessionFactory> sessionFactories;
    protected final ThreadLocal<Map<String, Session>> threadSessions;

    protected final Logger logger = LogUtil.getLogger(MDContext.class);

    //--------------------------------------------------------------------------
    // Constructors
    //--------------------------------------------------------------------------

    public MDContext() {
        threadSessions = new ThreadLocal<Map<String, Session>>();
    }

    //--------------------------------------------------------------------------
    // Model loading
    //--------------------------------------------------------------------------

    public void loadXmlModelAsResource(String resource) {
        LogUtil.entering(logger, "loadXmlModelAsResource", resource);

        DBParser parser = new DBParser();
        try {
            dataModel = parser.parse(resource);
            HibernateConfig builder = new HibernateConfig();
            sessionFactories = builder.build(dataModel);
        } catch (Exception e) {
            LogUtil.logMF(logger, Level.SEVERE, e,
                    "Cannot load/parse model: {0}", resource);
        }

        LogUtil.exiting(logger, "loadXmlModelAsResource");
    }

    //--------------------------------------------------------------------------
    // Model access
    //--------------------------------------------------------------------------

    public List<Table> getAllTables() {
        List<Table> result = new ArrayList<Table>();
        for (Database database : dataModel.getDatabases()) {
            for (Schema schema : database.getSchemas()) {
                for (Table table : schema.getTables()) {
                    result.add(table);
                }
            }
        }
        return result;
    }

    public Database findDatabaseByName(String databaseName)
            throws ModelObjectNotFoundException {
        for (Database database : dataModel.getDatabases()) {
            if (database.getDatabaseName().equals(databaseName)) {
                return database;
            }
        }
        throw new ModelObjectNotFoundException(databaseName);
    }

    public Schema findSchemaByQualifiedName(String qualifiedSchemaName)
            throws ModelObjectNotFoundException {
        int lastDot = qualifiedSchemaName.lastIndexOf(".");
        String databaseName = qualifiedSchemaName.substring(0, lastDot);
        String schemaName = qualifiedSchemaName.substring(lastDot + 1);
        Database database = findDatabaseByName(databaseName);
        for (Schema schema : database.getSchemas()) {
            if (schema.getSchemaName().equals(schemaName)) {
                return schema;
            }
        }
        throw new ModelObjectNotFoundException(qualifiedSchemaName);
    }

    public Table findTableByQualifiedName(String qualifiedTableName)
            throws ModelObjectNotFoundException {
        int lastDot = qualifiedTableName.lastIndexOf(".");
        String qualifiedSchemaName = qualifiedTableName.substring(0, lastDot);
        String tableName = qualifiedTableName.substring(lastDot + 1);
        Schema schema = findSchemaByQualifiedName(qualifiedSchemaName);
        for (Table table : schema.getTables()) {
            if (table.getTableName().equals(tableName)) {
                return table;
            }
        }
        throw new ModelObjectNotFoundException(qualifiedTableName);
    }

    public Column findColumnByQualifiedName(String qualifiedColumnName)
            throws ModelObjectNotFoundException {
        int lastDot = qualifiedColumnName.lastIndexOf(".");
        String qualifiedTableName = qualifiedColumnName.substring(0, lastDot);
        String columnName = qualifiedColumnName.substring(lastDot + 1);
        Table table = findTableByQualifiedName(qualifiedTableName);
        for (Column column : table.getColumns()) {
            if (column.getColumnName().equals(columnName)) {
                return column;
            }
        }
        throw new ModelObjectNotFoundException(qualifiedColumnName);
    }

    public Relationship findOneToManyRelationship(Table table,
                                                  String relationshipName)
            throws ModelObjectNotFoundException {
        for (Relationship relationship : table.getOneToManyRelationships()) {
            if (relationship.getRelationshipName().equals(relationshipName)) {
                return relationship;
            }
        }
        throw new ModelObjectNotFoundException(relationshipName);
    }

    //--------------------------------------------------------------------------
    // Persistance
    //--------------------------------------------------------------------------

    public Map<String, Object> getObjectByPk(String qualifiedTableName,
                                             Object... pk) {
        throw new UnsupportedOperationException();
    }
    
    public Map<String, Object> getObjectByPk(String qualifiedTableName,
                                             HashMap<String, Object> pk) {
        Table table;
        try {
            table = findTableByQualifiedName(qualifiedTableName);
        } catch (ModelObjectNotFoundException e) {
            throw new Error(e);
        }
        String databaseName = table.getDatabaseName();
        Session session = threadSessions.get().get(databaseName);

        //noinspection unchecked
        return (Map<String, Object>)session.load(qualifiedTableName, pk);
    }


    public List<Map<String, Object>> getAllObjects(String qualifiedTableName) {
        Table table;
        try {
            table = findTableByQualifiedName(qualifiedTableName);
        } catch (ModelObjectNotFoundException e) {
            throw new Error(e);
        }
        String databaseName = table.getDatabaseName();
        Session session = threadSessions.get().get(databaseName);

        //noinspection unchecked
        return (List<Map<String, Object>>)session.createQuery(
                            "from " + qualifiedTableName).list();
    }

    // lasciare per ultima
    public List<Map<String, Object>> getObjects(String qualifiedTableName,
                                                Criteria criteria) {
        throw new UnsupportedOperationException();
    }

    public void saveOrUpdateObject(Map<String, Object> obj) {
        Table table;
        try {
            table = findTableByQualifiedName((String) obj.get("$type$"));
        } catch (ModelObjectNotFoundException e) {
            throw new Error(e);
        }
        String databaseName = table.getDatabaseName();
                Session session = threadSessions.get().get(databaseName);
        session.beginTransaction();
        session.saveOrUpdate((String) obj.get("$type$"), obj);
        session.getTransaction().commit();
    }

    public void saveObject(Map<String, Object> obj) {
        Table table;
        try {
            table = findTableByQualifiedName((String) obj.get("$type$"));
        } catch (ModelObjectNotFoundException e) {
            throw new Error(e);
        }
        String databaseName = table.getDatabaseName();
                Session session = threadSessions.get().get(databaseName);
        session.beginTransaction();
        session.save((String) obj.get("$type$"), obj);
        session.getTransaction().commit();
    }

    public void updateObject(Map<String, Object> obj) {
        Table table;
        try {
            table = findTableByQualifiedName((String) obj.get("$type$"));
        } catch (ModelObjectNotFoundException e) {
            throw new Error(e);
        }
        String databaseName = table.getDatabaseName();
                Session session = threadSessions.get().get(databaseName);
        session.beginTransaction();
        session.update((String) obj.get("$type$"), obj);
        session.getTransaction().commit();
    }

    public void deleteObject(Map<String, Object> obj) {
        Table table;
        try {
            table = findTableByQualifiedName((String) obj.get("$type$"));
        } catch (ModelObjectNotFoundException e) {
            throw new Error(e);
        }
        String databaseName = table.getDatabaseName();
                Session session = threadSessions.get().get(databaseName);
        session.beginTransaction();
        session.delete((String) obj.get("$type$"), obj);
        session.getTransaction().commit();

    }

    public void openSession() {
        Map<String, Session> sessions = new HashMap<String, Session>();

        for (Map.Entry<String, SessionFactory> current : sessionFactories.entrySet()) {
            String databaseName = current.getKey();
            SessionFactory sessionFactory = current.getValue();
            Session session = sessionFactory.openSession();
            sessions.put(databaseName, session);
        }
        threadSessions.set(sessions);
    }


    public void closeSession() {
        Map<String, Session> sessions = threadSessions.get();

        for (Session current : sessions.values()) {
            current.close();
        }

        threadSessions.set(null);
    }

    public List<Map<String, Object>> getRelatedObjects(
            Map<String, Object> obj, String oneToManyRelationshipName) {
        if (obj.get(oneToManyRelationshipName) instanceof List){
            return (List<Map<String, Object>>)
                    obj.get(oneToManyRelationshipName);
        }
        String qualifiedTableName = (String)obj.get("$type$");
        Table table;
        Relationship relationship;
        try {
            table = findTableByQualifiedName(qualifiedTableName);
            relationship =
                    findOneToManyRelationship(table, oneToManyRelationshipName);
        } catch (ModelObjectNotFoundException e) {
            throw new Error(e);
        }
        Table fromTable =
                relationship.getFromTable();
        Session session =
                threadSessions.get().get(fromTable.getDatabaseName());
        Criteria criteria =
                session.createCriteria(fromTable.getQualifiedName());
        for (Reference reference : relationship.getReferences()) {
            Column fromColumn = reference.getFromColumn();
            Column toColumn = reference.getToColumn();
            criteria.add(Restrictions.eq(fromColumn.getColumnName(),
                    obj.get(toColumn.getColumnName())));
        }
        return (List<Map<String, Object>>)criteria.list();
    }
}
