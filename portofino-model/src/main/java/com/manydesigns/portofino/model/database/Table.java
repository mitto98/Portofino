/*
 * Copyright (C) 2005-2020 ManyDesigns srl.  All rights reserved.
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

package com.manydesigns.portofino.model.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.manydesigns.elements.annotations.Required;
import com.manydesigns.elements.util.ReflectionUtil;
import com.manydesigns.portofino.model.*;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EcoreFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
* @author Alessio Stalla       - alessio.stalla@manydesigns.com
*/
@XmlRootElement(name = "table")
@XmlAccessorType(XmlAccessType.NONE)
@XmlType(propOrder = {"tableName", "entityName", "shortName", "javaClass","annotations","columns","foreignKeys","primaryKey","selectionProviders"})
public class Table implements ModelObject, Annotated, Named, Unmarshallable {
    public static final String copyright =
            "Copyright (C) 2005-2020 ManyDesigns srl";

    //**************************************************************************
    // Fields
    //**************************************************************************

    protected final List<Column> columns;
    protected final List<ForeignKey> foreignKeys;
    protected final List<ModelSelectionProvider> selectionProviders;
    protected List<Annotation> annotations = new ArrayList<>();

    protected Schema schema;
    protected String tableName;

    protected String javaClass;

    protected String shortName;

    protected PrimaryKey primaryKey;

    protected EClass eClass;

    //**************************************************************************
    // Fields for wire-up
    //**************************************************************************

    protected final List<ForeignKey> oneToManyRelationships;
    protected Class<?> actualJavaClass;
    protected String entityName;

    //**************************************************************************
    // Logging
    //**************************************************************************

    public static final Logger logger = LoggerFactory.getLogger(Table.class);

    //**************************************************************************
    // Constructors and init
    //**************************************************************************


    public Table(EClass eClass) {
        this.eClass = eClass;
        columns = new ArrayList<>();
        foreignKeys = new ArrayList<>();
        oneToManyRelationships = new ArrayList<>();
        selectionProviders = new ArrayList<>();
    }

    public Table() {
        this(EcoreFactory.eINSTANCE.createEClass());
    }

    public Table(Schema schema) {
        this();
        setSchema(schema);
    }

    //**************************************************************************
    // ModelObject implementation
    //**************************************************************************

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getQualifiedName() {
        if(schema == null || schema.getQualifiedName() == null) {
            return tableName;
        }
        return MessageFormat.format("{0}.{1}", schema.getQualifiedName(), tableName);
    }

    @Override
    public String getName() {
        return getTableName();
    }

    public void setParent(Object parent) {
        setSchema((Schema) parent);
    }

    public void afterUnmarshal(Unmarshaller u, Object parent) {
        setSchema((Schema) parent);
    }

    public void reset() {
        actualJavaClass = null;
        oneToManyRelationships.clear();
    }

    public void init(Model model, Configuration configuration) {
        assert schema != null;
        assert tableName != null;
        
        // wire up javaClass
        actualJavaClass = ReflectionUtil.loadClass(javaClass);

        String baseEntityName;
        if (StringUtils.isEmpty(entityName)) {
            baseEntityName = DatabaseLogic.normalizeName(getTableName());
        } else {
            baseEntityName = DatabaseLogic.normalizeName(entityName);
        }

        String calculatedEntityName = baseEntityName;

        int i = 2;
        Database database = schema.getDatabase();
        while(true) {
            Table existing = DatabaseLogic.findTableByEntityName(database, calculatedEntityName);
            if (existing == null || existing == this) {
                break;
            }
            logger.warn("Entity name {} already taken, generating a new one", calculatedEntityName);
            calculatedEntityName = baseEntityName + "_" + (i++);
        }

        eClass.setName(calculatedEntityName);
    }

    public void link(Model model, Configuration configuration) {}

    public void visitChildren(ModelObjectVisitor visitor) {
        for (Annotation a : annotations) {
            visitor.visit(a);
        }

        for (Column column : columns) {
            visitor.visit(column);
        }

        if (primaryKey == null) {
            logger.warn("Table {} has no primary key", toString());
        } else {
            visitor.visit(primaryKey);
        }

        for (ForeignKey foreignKey : foreignKeys) {
            visitor.visit(foreignKey);
        }

        for(ModelSelectionProvider selectionProvider : selectionProviders) {
            visitor.visit(selectionProvider);
        }
    }

    //**************************************************************************
    // Getters/setter
    //**************************************************************************

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
        if(this.schema != null) {
            schema.getModelElement().getEClassifiers().add(getModelElement());
        }
    }

    @Required
    public String getDatabaseName() {
        return schema.getDatabaseName();
    }

    @Required
    public String getSchemaName() {
        return schema.getActualSchemaName();
    }

    @Required
    @XmlAttribute(required = true)
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @XmlAttribute(required = false)
    public String getJavaClass() {
        return javaClass;
    }

    public void setJavaClass(String javaClass) {
        this.javaClass = javaClass;
    }

    @XmlElementWrapper(name="columns")
    @XmlElement(name = "column",
            type = Column.class)
    @JsonProperty("columns")
    public List<Column> getColumns() {
        return columns;
    }

    @XmlElement()
    public PrimaryKey getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(PrimaryKey primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Class getActualJavaClass() {
        return actualJavaClass;
    }

    public void setActualJavaClass(Class actualJavaClass) {
        this.actualJavaClass = actualJavaClass;
        if(this.javaClass != null) {
            this.javaClass = actualJavaClass.getName();
        }
    }

    @XmlElementWrapper(name="foreignKeys")
    @XmlElement(name = "foreignKey",
            type = ForeignKey.class)
    public List<ForeignKey> getForeignKeys() {
        return foreignKeys;
    }

    @XmlAttribute()
    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getActualEntityName() {
        return eClass.getName();
    }

    public List<ForeignKey> getOneToManyRelationships() {
        return oneToManyRelationships;
    }

    @XmlElementWrapper(name = "annotations")
    @XmlElement(name = "annotation", type = Annotation.class)
    @NotNull
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    @XmlElementWrapper(name="selectionProviders")
    @XmlElement(name="query",type=DatabaseSelectionProvider.class)
    public List<ModelSelectionProvider> getSelectionProviders() {
        return selectionProviders;
    }

    @XmlAttribute(required = false)
    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    //**************************************************************************
    // toString() override
    //**************************************************************************

    @Override
    public String toString() {
        return MessageFormat.format("table {0}", getQualifiedName());
    }

    //**************************************************************************
    // Utility methods
    //**************************************************************************

    public static String composeQualifiedName(String databaseName,
                                              String schemaName,
                                              String tableName) {
        return MessageFormat.format(
                "{0}.{1}.{2}", databaseName, schemaName, tableName);
    }

    public EClass getModelElement() {
        return eClass;
    }
}
