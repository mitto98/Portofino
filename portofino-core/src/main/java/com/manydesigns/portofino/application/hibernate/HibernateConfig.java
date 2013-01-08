/*
 * Copyright (C) 2005-2012 ManyDesigns srl.  All rights reserved.
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
package com.manydesigns.portofino.application.hibernate;


import com.manydesigns.elements.reflection.JavaClassAccessor;
import com.manydesigns.elements.reflection.PropertyAccessor;
import com.manydesigns.portofino.PortofinoProperties;
import com.manydesigns.portofino.database.StringBooleanType;
import com.manydesigns.portofino.model.database.*;
import com.manydesigns.portofino.model.database.ForeignKey;
import liquibase.database.structure.ForeignKeyConstraintType;
import org.hibernate.FetchMode;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Mappings;
import org.hibernate.id.IncrementGenerator;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Table;
import org.hibernate.type.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * @author Giampiero Granatella - giampiero.granatella@manydesigns.com
 * @author Angelo    Lupo       - angelo.lupo@manydesigns.com
 * @author Paolo     Predonzani - paolo.predonzani@manydesigns.com
 */
public class HibernateConfig {

    protected final ConnectionProvider connectionProvider;
    protected final org.apache.commons.configuration.Configuration portofinoConfiguration;
    private static final boolean LAZY = true;
    public static final Logger logger =
            LoggerFactory.getLogger(HibernateConfig.class);

    //String values for the mapping of boolean values to CHAR/VARCHAR columns
    private String trueString = "T";
    private String falseString = "F";

    public HibernateConfig(ConnectionProvider connectionProvider,
                           org.apache.commons.configuration.Configuration portofinoConfiguration) {
        this.connectionProvider = connectionProvider;
        this.portofinoConfiguration = portofinoConfiguration;
    }

    public Configuration buildSessionFactory(Database database) {
        try {
            Configuration configuration = new Configuration();

            setupConnection(configuration);
            setupConfigurationProperties(configuration);

            Mappings mappings = configuration.createMappings();

            //Class Mapping
            classMapping(database, mappings);
            //One2Many Mapping
            o2mMapping(database, configuration, mappings);
            //Many2One Mapping
            m2oMapping(database, configuration, mappings);

            return configuration;
        }
        catch (Throwable ex) {
            // Make sure you log the exception, as it might be swallowed
            System.err.println("Initial SessionFactory creation failed." + ex);
            ex.printStackTrace();
            throw new ExceptionInInitializerError(ex);
        }
    }

    protected void setupConfigurationProperties(Configuration configuration) {
        configuration
                .setProperty("hibernate.current_session_context_class",
                        "org.hibernate.context.ThreadLocalSessionContext") //hb4: "org.hibernate.context.internal.ThreadLocalSessionContext"
                .setProperty("org.hibernate.hql.ast.AST", "true")
                .setProperty("hibernate.globally_quoted_identifiers", "false");
        configuration.setProperty("hibernate.show_sql",
                portofinoConfiguration.getString(
                        PortofinoProperties.HIBERNATE_SHOW_SQL));
        // mettendo la modalità dynamic map, non funzionano le entità mappate su bean.
        // configuration.setProperty("hibernate.default_entity_mode", "dynamic-map");
        /* Per abilitare 2nd-level cache
configuration.setProperty("hibernate.cache.region.factory_class",
    portofinoConfiguration.getString("hibernate.cache.region.factory_class"));
configuration.setProperty("hibernate.cache.use_second_level_cache",
    portofinoConfiguration.getString("hibernate.cache.use_second_level_cache"));
configuration.setProperty("hibernate.cache.use_query_cache",
    portofinoConfiguration.getString("hibernate.cache.use_query_cache"));*/
    }

    protected void setupConnection(Configuration configuration) {
        if(!connectionProvider.getDatabasePlatform().isDialectAutodetected()) {
            configuration.setProperty(
                    "hibernate.dialect",
                    connectionProvider.getDatabasePlatform().getHibernateDialect().getClass().getName());
        }
        if(connectionProvider instanceof JdbcConnectionProvider) {
            JdbcConnectionProvider jdbcConnectionProvider =
                    (JdbcConnectionProvider) connectionProvider;
            configuration.setProperty("hibernate.connection.url", jdbcConnectionProvider.getActualUrl());
            configuration.setProperty("hibernate.connection.driver_class", jdbcConnectionProvider.getDriver());
            if(jdbcConnectionProvider.getUsername() != null) {
                configuration.setProperty("hibernate.connection.username", jdbcConnectionProvider.getUsername());
            }
            if(jdbcConnectionProvider.getPassword() != null) {
                    configuration.setProperty("hibernate.connection.password", jdbcConnectionProvider.getPassword());
            }

            configuration.setProperty("hibernate.connection.provider_class",
                    portofinoConfiguration.getString(
                            PortofinoProperties.HIBERNATE_CONNECTION_PROVIDER_CLASS));
            configuration.setProperty("hibernate.c3p0.min_size",
                    portofinoConfiguration.getString(
                            PortofinoProperties.HIBERNATE_C3P0_MIN_SIZE));
            configuration.setProperty("hibernate.c3p0.max_size",
                    portofinoConfiguration.getString(
                            PortofinoProperties.HIBERNATE_C3P0_MAX_SIZE));
            configuration.setProperty("hibernate.c3p0.timeout",
                    portofinoConfiguration.getString(
                            PortofinoProperties.HIBERNATE_C3P0_TIMEOUT));
            configuration.setProperty("hibernate.c3p0.idle_test_period",
                    portofinoConfiguration.getString(
                            PortofinoProperties.HIBERNATE_C3P0_IDLE_TEST_PERIOD));
        } else if(connectionProvider instanceof JndiConnectionProvider) {
            JndiConnectionProvider jndiConnectionProvider =
                    (JndiConnectionProvider) connectionProvider;
            configuration.setProperty("hibernate.connection.datasource", jndiConnectionProvider.getJndiResource());
        } else {
            throw new Error("Unsupported connection provider: " + connectionProvider);
        }
    }

    private Mappings classMapping(Database database, Mappings mappings) {

        for (Schema schema : database.getSchemas()) {
            for (com.manydesigns.portofino.model.database.Table aTable :
                    schema.getTables()) {
                logger.debug("Class - {}", aTable.getQualifiedName());
                com.manydesigns.portofino.model.database.PrimaryKey primaryKey =
                        aTable.getPrimaryKey();
                if (primaryKey == null) {
                    logger.debug("Skipping table without primary key: {}",
                            aTable.getQualifiedName());
                    continue;
                }
                if (!primaryKey.isValid()) {
                    logger.debug("Skipping table with invalid primary key: {}",
                            aTable.getQualifiedName());
                    continue;
                }
                RootClass clazz = createTableMapping(mappings, aTable);
                if(clazz != null) {
                    mappings.addClass(clazz);
                    mappings.addImport(clazz.getEntityName(), clazz.getEntityName());
                }
            }
        }
        return mappings;
    }
    private void m2oMapping(Database database, Configuration configuration, Mappings mappings) {
        for (Schema schema : database.getSchemas()) {
            for (com.manydesigns.portofino.model.database.Table aTable :
                    schema.getTables()) {
                for (ForeignKey rel : aTable.getForeignKeys()) {
                    logger.debug(MessageFormat.format("Many to one - {0} {1}",
                            aTable.getQualifiedName(), rel.getName()));
                    createM2O(configuration, mappings, rel);
                }
            }
        }
    }

    private void o2mMapping(Database database, Configuration configuration, Mappings mappings) {
        for (Schema schema : database.getSchemas()) {
            for (com.manydesigns.portofino.model.database.Table aTable :
                    schema.getTables()) {
                for (ForeignKey rel : aTable.getOneToManyRelationships()) {
                     logger.debug(MessageFormat.format("One to many - {0} {1}",
                                aTable.getQualifiedName(), rel.getName()));
                    createO2M(configuration, mappings, rel);
                }
            }
        }
    }

    protected RootClass createTableMapping(Mappings mappings,
                                           com.manydesigns.portofino.model.database.Table aTable) {


        Table tab = mappings.addTable(escapeName(aTable.getSchemaName()), null,
                escapeName(aTable.getTableName()), null, false);
        //tab.setName(escapeName(aTable.getTableName()));
        //tab.setSchema(escapeName(aTable.getSchemaName()));
        mappings.addTableBinding(aTable.getSchemaName(), null,
                aTable.getTableName(), aTable.getTableName(), null);

        RootClass clazz = new RootClass();
        clazz.setEntityName(aTable.getActualEntityName());
        clazz.setJpaEntityName(aTable.getActualEntityName());
        if (aTable.getJavaClass() != null) {
            clazz.setClassName(aTable.getJavaClass());
            clazz.setProxyInterfaceName(aTable.getJavaClass());
        }
        clazz.setLazy(LAZY);
        clazz.setTable(tab);
        //clazz.setNodeName(aTable.getTableName());

        List<com.manydesigns.portofino.model.database.Column> columnList =
                new ArrayList<com.manydesigns.portofino.model.database.Column>();

        for(com.manydesigns.portofino.model.database.Column modelColumn : aTable.getColumns()) {
            int jdbcType = modelColumn.getJdbcType();
            Class javaType = modelColumn.getActualJavaType();
            
            //First param = null ==> doesn't really set anything, just check
            boolean hibernateTypeOk =
                    setHibernateType(null, modelColumn, javaType, jdbcType);
            if (hibernateTypeOk) {
                columnList.add(modelColumn);
            } else {
                logger.error("Cannot find Hibernate type for table: {}, column: {}, jdbc type: {}, type name: {}. Skipping column.",
                        new Object[]{
                                aTable.getQualifiedName(),
                                modelColumn.getColumnName(),
                                jdbcType,
                                javaType != null ? javaType.getName() : null
                        });
            }
        }

        //Primary keys
        List<com.manydesigns.portofino.model.database.Column> columnPKList
                = aTable.getPrimaryKey().getColumns();

        if(!columnList.containsAll(columnPKList)) {
            logger.error("Primary key refers to some invalid columns, skipping table {}", aTable.getQualifiedName());
            return null;
        }

        if (columnPKList.size() > 1) {
            createPKComposite(mappings, aTable, aTable.getPrimaryKey().getPrimaryKeyName(),
                    clazz, tab, columnPKList);
        } else {
            createPKSingle(mappings, aTable, aTable.getPrimaryKey().getPrimaryKeyName(),
                    clazz, tab, columnPKList);
        }

        //Other columns
        columnList.removeAll(columnPKList);

        for (com.manydesigns.portofino.model.database.Column column
                : columnList) {
            Column col = createColumn(mappings, tab, column);
            if(col != null) {
                clazz.addProperty(createProperty(column, col.getValue()));
            }
        }

        return clazz;
    }

    protected Column createColumn(Mappings mappings,
                                Table tab,
                                com.manydesigns.portofino.model.database.Column column) {
        Column col = new Column();
        col.setName(escapeName(column.getColumnName()));
        col.setLength(column.getLength());
        col.setPrecision(column.getLength());
        col.setScale(column.getScale());
        col.setNullable(column.isNullable());
        String columnType = column.getColumnType();
        int jdbcType = column.getJdbcType();

        col.setSqlTypeCode(jdbcType);
        col.setSqlType(columnType);

        SimpleValue value = new SimpleValue(mappings, tab);
        if (!setHibernateType(value, column, column.getActualJavaType(), jdbcType)) {
            logger.error("Skipping column {}", column.getQualifiedName());
            return null;
        }

        value.addColumn(col);
        tab.addColumn(col);
        mappings.addColumnBinding(column.getColumnName(), col, tab);

        return col;
    }

    protected Property createProperty(com.manydesigns.portofino.model.database.Column column,
                                      Value value) {
        Property prop = new Property();
        prop.setName(column.getActualPropertyName());
        //prop.setNodeName(column.getActualPropertyName());
        prop.setValue(value);
        return prop;
    }

    protected void createPKComposite(Mappings mappings,
                                     com.manydesigns.portofino.model.database.Table mdTable,
                                     String pkName, RootClass clazz,
                                     Table tab,
                                     List<com.manydesigns.portofino.model.database.Column> columnPKList) {


        PrimaryKey primaryKey = new PrimaryKey();
        primaryKey.setName(pkName);
        primaryKey.setTable(tab);

        clazz.setEmbeddedIdentifier(true);
        Component component = new Component(mappings, clazz);
        component.setDynamic(mdTable.getActualJavaClass()==null);
        String name;
        name = mdTable.getQualifiedName();

        component.setRoleName(name + ".id");
        component.setEmbedded(true);
        //component.setNodeName("id");
        component.setKey(true);
        component.setNullValue("undefined");

        if (!component.isDynamic()){
            component.setComponentClassName
                    (mdTable.getJavaClass()); //TODO verificare se non si intende actualJavaClass
        }

        boolean hasErrors = false;
        for (com.manydesigns.portofino.model.database.Column
                column : columnPKList) {
            if (column == null ) {
                throw new InternalError("Null column");
            }

            Column col = createColumn(mappings, tab, column);

            hasErrors = col == null || hasErrors;

            if(col != null) {
                primaryKey.addColumn(col);
                Property prop = createProperty(column, col.getValue());
                prop.setCascade("none");
                //prop.setPropertyAccessorName("property"); interferisce con il generator più sotto
                prop.setPersistentClass(clazz);
                component.addProperty(prop);

                //Generator not supported for embedded map identifier
                //See https://forum.hibernate.org/viewtopic.php?t=945273
                //See Component.buildIdentifierGenerator()
                /*String columnName = column.getColumnName();
                PrimaryKeyColumn pkCol = mdTable.getPrimaryKey().findPrimaryKeyColumnByName(columnName);
                if(pkCol == null) {
                    logger.error("Column without corresponding PrimaryKeyColumn: {}", columnName);
                    hasErrors = true;
                    continue;
                }
                Generator generator = pkCol.getGenerator();
                setPKColumnGenerator(mappings, clazz, tab, column, value, generator);*/
            }
        }
        if (hasErrors) {
            // TODO PAOLO: se la PK non e' buona, tutta la tabella dovrebbe saltare
            logger.error("Skipping primary key");
            return;
        }

        tab.setIdentifierValue(component);
        clazz.setIdentifier(component);
        clazz.setDiscriminatorValue(name);

        tab.setPrimaryKey(primaryKey);
    }


    protected void createPKSingle(Mappings mappings,
                                  com.manydesigns.portofino.model.database.Table mdTable,
                                  String pkName, RootClass clazz,
                                  Table tab,
                                  List<com.manydesigns.portofino.model.database.Column> columnPKList) {
        PrimaryKeyColumn pkcol =mdTable.getPrimaryKey().getPrimaryKeyColumns().get(0);
        com.manydesigns.portofino.model.database.Column
                column = columnPKList.get(0);
        final PrimaryKey primaryKey = new PrimaryKey();
        primaryKey.setName(pkName);
        primaryKey.setTable(tab);
        tab.setPrimaryKey(primaryKey);

        Column col = createColumn(mappings, tab, column);

        if (col == null) {
            // TODO PAOLO: se la PK non e' buona, tutta la tabella dovrebbe saltare
            logger.error("Skipping primary key");
            return;
        }

        SimpleValue id = (SimpleValue) col.getValue();
        //Make the defaults explicit. See section 5.1.4.5. Assigned identifiers in the Hibernate reference
        //(http://docs.jboss.org/hibernate/core/3.3/reference/en/html/mapping.html)
        id.setIdentifierGeneratorStrategy("assigned");
        id.setNullValue("undefined");

        tab.getPrimaryKey().addColumn(col);
        
        Property prop = createProperty(column, id);
        clazz.addProperty(prop);
        prop.setPropertyAccessorName(mappings.getDefaultAccess());
        //PropertyGeneration generation = PropertyGeneration.parse(null);
        //prop.setGeneration(generation);

        prop.setInsertable(false);
        prop.setUpdateable(false);

        Generator generator = pkcol.getGenerator();

        setPKColumnGenerator(mappings, clazz, tab, column, id, generator);

        tab.setIdentifierValue(id);
        clazz.setIdentifier(id);
        clazz.setIdentifierProperty(prop);
        clazz.setDiscriminatorValue(mdTable.getQualifiedName());

    }

    protected void setPKColumnGenerator(Mappings mappings, RootClass clazz, Table tab, com.manydesigns.portofino.model.database.Column column, SimpleValue id, Generator generator) {
        if (column.isAutoincrement()) {
            manageIdentityGenerator(mappings, tab, id);
        } else if (generator != null) {
            if (generator instanceof SequenceGenerator) {
                manageSequenceGenerator(mappings, tab, id, (SequenceGenerator) generator);
            } else if (generator instanceof
                                com.manydesigns.portofino.model.database.TableGenerator) {
                manageTableGenerator(mappings, tab, id,
                        (com.manydesigns.portofino.model.database.TableGenerator) generator);
            } else if (generator instanceof
                                com.manydesigns.portofino.model.database.IncrementGenerator){
                manageIncrementGenerator(mappings, tab, id, clazz.getEntityName());
            }
        }
    }

    private void manageIdentityGenerator(Mappings mappings, Table tab,
                                          SimpleValue id) {
        id.setIdentifierGeneratorStrategy("identity");
        Properties params = new Properties();
        params.put(PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER,
                    mappings.getObjectNameNormalizer());

        params.setProperty(
                    PersistentIdentifierGenerator.SCHEMA,
                    escapeName(tab.getSchema()));
        id.setIdentifierGeneratorProperties(params);
        id.setNullValue(null);
    }

    private void manageSequenceGenerator(Mappings mappings, Table tab,
                                          SimpleValue id, SequenceGenerator generator) {
        id.setIdentifierGeneratorStrategy
                ("enhanced-sequence");
        Properties params = new Properties();
        params.put(PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER,
                    mappings.getObjectNameNormalizer());
        params.put(SequenceStyleGenerator.SEQUENCE_PARAM,
                    escapeName(generator.getName()));
        params.setProperty(
                SequenceStyleGenerator.SCHEMA,
                escapeName(tab.getSchema()));
        id.setIdentifierGeneratorProperties(params);
        id.setNullValue(null);
    }

    private void manageTableGenerator(Mappings mappings, Table tab, SimpleValue id,
                                          com.manydesigns.portofino.model.database.TableGenerator generator) {
        id.setIdentifierGeneratorStrategy("enhanced-table");
        Properties params = new Properties();
        params.put(TableGenerator.TABLE,
                    tab);
        params.put(TableGenerator.TABLE_PARAM,
                    escapeName(generator.getTable()));
        params.put(PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER,
                    mappings.getObjectNameNormalizer());
        params.put(TableGenerator.SEGMENT_COLUMN_PARAM, escapeName(generator.getKeyColumn()));
        params.put(TableGenerator.SEGMENT_VALUE_PARAM, generator.getKeyValue());
        params.put(TableGenerator.VALUE_COLUMN_PARAM,escapeName(generator.getValueColumn()));
        params.setProperty(
                    TableGenerator.SCHEMA,escapeName(tab.getSchema()));
        id.setIdentifierGeneratorProperties(params);
        id.setNullValue(null);
    }

    private void manageIncrementGenerator(Mappings mappings, Table tab, SimpleValue id, String entityName) {
        id.setIdentifierGeneratorStrategy("increment");
        Properties params = new Properties();
        params.put(PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER,
                mappings.getObjectNameNormalizer());
        params.setProperty(PersistentIdentifierGenerator.SCHEMA, escapeName(tab.getSchema()));
        params.put(IncrementGenerator.ENTITY_NAME,
                entityName);
        id.setIdentifierGeneratorProperties(params);
        id.setNullValue(null);
    }

    protected void createO2M(
            Configuration config,
            Mappings mappings,
            ForeignKey relationship) {

        com.manydesigns.portofino.model.database.Table manyMDTable =
                relationship.getFromTable();
        com.manydesigns.portofino.model.database.Table oneMDTable =
                relationship.getToTable();

        //Se la classe One non e' dinamica e
        // non ha la proprieta' non inserisco la relazione
        if (oneMDTable.getJavaClass()!=null){
            try {
                Class oneClass = oneMDTable.getActualJavaClass();
                JavaClassAccessor accessor = JavaClassAccessor
                        .getClassAccessor(oneClass);
                PropertyAccessor[] propertyAccessors = accessor.getProperties();
                boolean found = false;
                for (PropertyAccessor propertyAccessor : propertyAccessors){
                    if (propertyAccessor.getName()
                            .equals(relationship.getActualManyPropertyName())) {
                        found=true;
                    }
                }
                if(!found){
                    logger.warn("Property '{}' not found, skipping relationship {}",
                                relationship.getActualManyPropertyName(),
                                relationship.getQualifiedName());
                    return;
                }
            } catch (Exception e) {
                //se non c'e' non inserisco la relazione
                logger.warn("Property not found, skipping relationship ", e);
                return;
            }
        }
        //relazione virtuali fra Database differenti
        if(!manyMDTable.getDatabaseName().equalsIgnoreCase(oneMDTable.getDatabaseName())){
            logger.warn("Relationship crosses databases, skipping: {}", relationship.getQualifiedName());
            return;
        }

        String manyMDQualifiedTableName = manyMDTable.getActualEntityName();
        String oneMDQualifiedTableName = oneMDTable.getActualEntityName();

        PersistentClass clazzOne =
                config.getClassMapping(oneMDQualifiedTableName);
        if (clazzOne == null) {
            logger.error("Cannot find table '{}' as 'one' side of foreign key '{}'. Skipping relationship.",
                    oneMDQualifiedTableName, relationship.getName());
            return;
        }

        PersistentClass clazzMany =
                config.getClassMapping(manyMDQualifiedTableName);
        if (clazzMany == null) {
            logger.error("Cannot find table '{}' as 'many' side of foreign key '{}'. Skipping relationship.",
                    manyMDQualifiedTableName, relationship.getName());
            return;
        }

        //Uso i Bag perche' i set non funzionano con i componenti dinamici
        Bag set = new Bag(mappings, clazzOne);
        // Mettere Lazy in debug a false per ottenere subito eventuali errori
        // nelle relazioni
        set.setLazy(LAZY);

        set.setRole(relationship.getToTable().getActualEntityName()+"."+relationship.getActualManyPropertyName());
        //set.setNodeName(relationship.getActualManyPropertyName());
        set.setCollectionTable(clazzMany.getTable());
        OneToMany oneToMany = new OneToMany(mappings, set.getOwner());
        set.setElement(oneToMany);


        oneToMany.setReferencedEntityName(manyMDQualifiedTableName);

        oneToMany.setAssociatedClass(clazzMany);
        oneToMany.setEmbedded(true);
        
        set.setSorted(false);
        set.setFetchMode(FetchMode.DEFAULT);
        //Riferimenti alle colonne

        DependantValue dv;
        Table tableMany = clazzMany.getTable();
        Table tableOne = clazzOne.getTable();
        List<Column> oneColumns = new ArrayList<Column>();
        List<Column> manyColumns = new ArrayList<Column>();
        //Chiave multipla
        final List<Reference> refs = relationship.getReferences();
        if (refs.size() > 1) {
            dv = createFKComposite(mappings, relationship, manyMDTable, clazzOne,
                    clazzMany, set, tableMany, tableOne, oneColumns, manyColumns);
        } else {  //chiave straniera singola
            dv = createFKSingle(mappings, clazzOne, clazzMany, tableOne,
                    oneColumns, manyColumns, refs);
        }

        tableMany.createForeignKey(relationship.getName(),
                manyColumns,
                oneMDQualifiedTableName,
                oneColumns);

        dv.setNullable(false);
        set.setKey(dv);
        mappings.addCollection(set);

        Property prop = new Property();
        prop.setName(relationship.getActualManyPropertyName());
        //prop.setNodeName(relationship.getActualManyPropertyName());
        prop.setValue(set);
        if (ForeignKeyConstraintType.importedKeyCascade.name()
                .equalsIgnoreCase(relationship.getOnDelete())){
            prop.setCascade("delete");
        } else {
            prop.setCascade("none");
        }
        clazzOne.addProperty(prop);

        //if(!StringUtils.)
    }

    private DependantValue createFKComposite(
            Mappings mappings,
            com.manydesigns.portofino.model.database.ForeignKey relationship,
            com.manydesigns.portofino.model.database.Table manyMDTable,
            PersistentClass clazzOne,
            PersistentClass clazzMany, Bag set,
            Table tableMany, Table tableOne,
            List<Column> oneColumns,
            List<Column> manyColumns) {
        DependantValue dv;
        Component component = new Component(mappings, set);
        component.setDynamic(manyMDTable.getActualJavaClass()==null);
        component.setEmbedded(true);
        dv = new DependantValue(mappings, clazzMany.getTable(), component);
        dv.setNullable(true);
        dv.setUpdateable(true);


        for (Reference ref : relationship.getReferences()) {
            String colToName = ref.getToColumn();
            String colToPropertyName = ref.getActualToColumn().getActualPropertyName();
            String colFromName = ref.getFromColumn();
            Iterator it = tableMany.getColumnIterator();
            while (it.hasNext()) {
                Column col = (Column) it.next();
                if (col.getName().equals(colFromName)) {
                    dv.addColumn(col);
                    manyColumns.add(col);
                    break;
                }
            }

            Iterator it2 = tableOne.getColumnIterator();
            while (it2.hasNext()) {
                Column col = (Column) it2.next();
                if (col.getName().equals(colToName)) {
                    oneColumns.add(col);
                    break;
                }
            }
            Property refProp;
            refProp = getRefProperty(clazzOne, colToPropertyName);
            component.addProperty(refProp);
        }
        return dv;
    }

    private DependantValue createFKSingle(
            Mappings mappings, PersistentClass clazzOne,
            PersistentClass clazzMany, Table tableOne, List<Column> oneColumns,
            List<Column> manyColumns, List<Reference> refs) {
        DependantValue dv;
        Property refProp;

        Reference reference = refs.get(0);
        String colFromName = reference.getFromColumn();
        String colToName = reference.getToColumn();
        String colToPropertyName = reference.getActualToColumn().getActualPropertyName();
        refProp = getRefProperty(clazzOne, colToPropertyName);
        dv = new DependantValue(mappings, clazzMany.getTable(),
                refProp.getPersistentClass().getKey());
        dv.setNullable(true);
        dv.setUpdateable(true);

        Iterator it = clazzMany.getTable().getColumnIterator();
        while (it.hasNext()) {
            Column col = (Column) it.next();
            if (col.getName().equals(colFromName)) {
                dv.addColumn(col);
                manyColumns.add(col);
                break;
            }
        }

        Iterator it2 = tableOne.getColumnIterator();
        while (it2.hasNext()) {
            Column col = (Column) it2.next();
            if (col.getName().equals(colToName)) {
                oneColumns.add(col);
                break;
            }
        }
        return dv;
    }


    protected void createM2O(Configuration config, Mappings mappings,
                             ForeignKey relationship) {
        com.manydesigns.portofino.model.database.Table manyMDTable =
                relationship.getFromTable();
        com.manydesigns.portofino.model.database.Table oneMDTable =
                relationship.getToTable();
        String manyMDQualifiedTableName = manyMDTable.getActualEntityName();
        String oneMDQualifiedTableName = oneMDTable.getActualEntityName();

        RootClass clazz =
                (RootClass) mappings.getClass(manyMDQualifiedTableName);
        if(clazz == null) {
            logger.error("Cannot find table '{}' as 'many' side of foreign key '{}'. Skipping relationship.",
                    manyMDQualifiedTableName, relationship.getName());
            return;
        }

        Table tab = clazz.getTable();
        List<String> columnNames = new ArrayList<String>();

        for (Reference ref : relationship.getReferences()) {
            if(ref.getActualFromColumn() == null) {
                logger.error("Missing from column {}, skipping relationship", ref.getFromColumn());
                return;
            }
            columnNames.add(ref.getFromColumn());
        }

        ManyToOne m2o = new ManyToOne(mappings, tab);
        m2o.setLazy(LAZY);
        final HashMap<String, PersistentClass> persistentClasses =
                new HashMap<String, PersistentClass>();
        persistentClasses.put(oneMDQualifiedTableName,
                config.getClassMapping(oneMDQualifiedTableName));
        m2o.setReferencedEntityName(oneMDQualifiedTableName);
        m2o.createPropertyRefConstraints(persistentClasses);

        PersistentClass manyClass = config.getClassMapping(manyMDQualifiedTableName);
        for (String columnName : columnNames) {
            Column col = new Column();
            col.setName(escapeName(columnName));
            //Recupero la colonna precedentemente associata alla tabella:
            //essa ha uno uniqueIdentifier generato al momento dell'associazione alla tabella;
            //questo viene utilizzato per disambiguare l'alias della colonna nelle query
            //SQL generate da Hibernate.
            col = manyClass.getTable().getColumn(col);
            if(col == null) {
                logger.error("Column not found in 'many' entity {}: {}, " +
                             "skipping relationship", manyClass.getEntityName(), columnName);
                return;
            }
            m2o.addColumn(col);
        }

        Property prop = new Property();
        prop.setName(relationship.getActualOnePropertyName());
        //prop.setNodeName(relationship.getActualOnePropertyName());
        prop.setValue(m2o);
        prop.setCascade("none"); //TODO era "all", capire
        prop.setInsertable(false);
        prop.setUpdateable(false);
        clazz.addProperty(prop);
    }

    private Property getRefProperty(PersistentClass clazzOne, String propertyName) {
        Property refProp;
        //TODO alessio ha senso questo automatismo?
        if (null != clazzOne.getIdentifierProperty()) {
            refProp = clazzOne.getIdentifierProperty();
        } else if (null != clazzOne.getIdentifier()) {
            refProp = ((Component) clazzOne.getIdentifier())
                    .getProperty(propertyName);
        } else {
            refProp = clazzOne.getProperty(propertyName);
        }
        return refProp;
    }

    private String escapeName(String name) {
        // Portofino handles all tables in a case-sensitive way
        return "`"+name+"`";
    }

    public boolean setHibernateType(@Nullable SimpleValue value,
                                 com.manydesigns.portofino.model.database.Column column,
                                 Class javaType,
                                 final int jdbcType) {
        String typeName;
        Properties typeParams = null;
        if(javaType == null) {
            return false;
        }
        if (javaType == Long.class) {
            typeName = LongType.INSTANCE.getName();
        } else if (javaType == Short.class) {
            typeName = ShortType.INSTANCE.getName();
        } else if (javaType == Integer.class) {
            typeName = IntegerType.INSTANCE.getName();
        } else if (javaType == Byte.class) {
            typeName = ByteType.INSTANCE.getName();
        } else if (javaType == Float.class) {
            typeName = FloatType.INSTANCE.getName();
        } else if (javaType == Double.class) {
            typeName = DoubleType.INSTANCE.getName();
        } else if (javaType == Character.class) {
            typeName = CharacterType.INSTANCE.getName();
        } else if (javaType == String.class) {
            typeName = StringType.INSTANCE.getName();
        } else if (java.util.Date.class.isAssignableFrom(javaType)) {
            switch (jdbcType) {
                case Types.DATE:
                    typeName = DateType.INSTANCE.getName();
                    break;
                case Types.TIME:
                    typeName = TimeType.INSTANCE.getName();
                    break;
                case Types.TIMESTAMP:
                    typeName = TimestampType.INSTANCE.getName();
                    break;
                default:
                    typeName = null;
            }
        } else if (javaType == Boolean.class) {
            if(jdbcType == Types.BIT || jdbcType == Types.BOOLEAN) {
                typeName = BooleanType.INSTANCE.getName();
            } else if(jdbcType == Types.NUMERIC || jdbcType == Types.DECIMAL || jdbcType == Types.INTEGER ||
                      jdbcType == Types.SMALLINT || jdbcType == Types.TINYINT || jdbcType == Types.BIGINT) {
                typeName = NumericBooleanType.INSTANCE.getName();
            } else if(jdbcType == Types.CHAR || jdbcType == Types.VARCHAR) {
                typeName = StringBooleanType.class.getName();
                typeParams = new Properties();
                typeParams.setProperty("true", trueString != null ? trueString : StringBooleanType.NULL);
                typeParams.setProperty("false", falseString != null ? falseString : StringBooleanType.NULL);
                typeParams.setProperty("sqlType", String.valueOf(jdbcType));
            } else {
                typeName = null;
            }
        } else if (javaType == BigDecimal.class) {
            typeName = BigDecimalType.INSTANCE.getName();
        } else if (javaType == BigInteger.class) {
            typeName = BigIntegerType.INSTANCE.getName();
        } else if (javaType == byte[].class) {
            typeName = BlobType.INSTANCE.getName();
        } else {
            typeName = null;
        }

        if (typeName == null) {
            logger.error("Unsupported type (java type: {}, jdbc type: {}) " +
                    "for column '{}'.",
                    new Object[] {
                            javaType,
                            jdbcType,
                            column.getColumnName()
                    });
            return false;
        }

        if (value != null) {
            value.setTypeName(typeName);
            if(typeParams != null) {
                value.setTypeParameters(typeParams);
            }
        }
        return true;
    }

    public String getTrueString() {
        return trueString;
    }

    public void setTrueString(String trueString) {
        this.trueString = trueString;
    }

    public String getFalseString() {
        return falseString;
    }

    public void setFalseString(String falseString) {
        this.falseString = falseString;
    }
}
