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

package com.manydesigns.portofino.database;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
* @author Alessio Stalla       - alessio.stalla@manydesigns.com
*/
public class Type {
    public static final String copyright =
            "Copyright (c) 2005-2012, ManyDesigns srl";

    //**************************************************************************
    // Logger
    //**************************************************************************

    public final static Logger logger = LoggerFactory.getLogger(Type.class);

    //**************************************************************************
    // Fields
    //**************************************************************************

    protected final String typeName;
    protected final int jdbcType;
    protected final boolean autoincrement;
    protected final Long maximumPrecision;
    protected final String literalPrefix;
    protected final String literalSuffix;
    protected final boolean nullable;
    protected final boolean caseSensitive;
    protected final boolean searchable;
    protected final short minimumScale;
    protected final short maximumScale;
    protected final Boolean precisionRequired;
    protected final Boolean scaleRequired;


    //**************************************************************************
    // Constructors
    //**************************************************************************

    public Type(String typeName, int jdbcType, Long maximumPrecision,
                String literalPrefix, String literalSuffix,
                boolean nullable, boolean caseSensitive, boolean searchable,
                boolean autoincrement, short minimumScale, short maximumScale) {
        this.typeName = typeName;
        this.jdbcType = jdbcType;
        this.maximumPrecision = maximumPrecision;
        this.literalPrefix = literalPrefix;
        this.literalSuffix = literalSuffix;
        this.nullable = nullable;
        this.caseSensitive = caseSensitive;
        this.searchable = searchable;
        this.autoincrement = autoincrement;
        this.minimumScale = minimumScale;
        this.maximumScale = maximumScale;

        this.precisionRequired = null;
        this.scaleRequired = null;

    }

    public Type(String typeName, int jdbcType, Long maximumPrecision,
                String literalPrefix, String literalSuffix,
                boolean nullable, boolean caseSensitive, boolean searchable,
                boolean autoincrement, short minimumScale, short maximumScale,
                boolean precisionRequired, boolean scaleRequired) {
        this.typeName = typeName;
        this.jdbcType = jdbcType;
        this.maximumPrecision = maximumPrecision;
        this.literalPrefix = literalPrefix;
        this.literalSuffix = literalSuffix;
        this.nullable = nullable;
        this.caseSensitive = caseSensitive;
        this.searchable = searchable;
        this.autoincrement = autoincrement;
        this.minimumScale = minimumScale;
        this.maximumScale = maximumScale;

        this.precisionRequired = precisionRequired;
        this.scaleRequired = scaleRequired;

    }

    //**************************************************************************
    // Getters/setter
    //**************************************************************************

    public String getTypeName() {
        return typeName;
    }

    public int getJdbcType() {
        return jdbcType;
    }

    public Class getDefaultJavaType() {
        return getDefaultJavaType(jdbcType, maximumPrecision, maximumScale);
    }

    public static @Nullable Class getDefaultJavaType(int jdbcType, long precision, int scale) {
        switch (jdbcType) {
            case Types.BIGINT:
                return Long.class;
            case Types.BIT:
                return Boolean.class;
            case Types.BOOLEAN:
                return Boolean.class;
            case Types.CHAR:
            case Types.VARCHAR:
            case -15: // Types.NCHAR is on JDBC4/Java6 - we try to stay compatible with JDBC3/Java5
            case -9:  // Types.NVARCHAR (same)
                return String.class;
            case Types.DATE:
                return java.sql.Date.class;
            case Types.TIME:
                return Time.class;
            case Types.TIMESTAMP:
                return Timestamp.class;
            case Types.DECIMAL:
            case Types.NUMERIC:
                if(scale > 0) {
                    return BigDecimal.class;
                } else {
                    return getDefaultIntegerType(precision);
                }
            case Types.DOUBLE:
                return Double.class;
            case Types.REAL:
                return Double.class;
            case Types.FLOAT:
                return Float.class;
            case Types.INTEGER:
                return getDefaultIntegerType(precision);
            case Types.SMALLINT:
                return Short.class;
            case Types.TINYINT:
                return Byte.class;
            case Types.BINARY:
                return byte[].class;
            case Types.BLOB:
                 return java.sql.Blob.class;
            case Types.CLOB:
                return String.class;
            case Types.LONGVARBINARY:
                return byte[].class;
            case Types.LONGVARCHAR:
                return java.lang.String.class;
            case Types.VARBINARY:
                return byte[].class;
            case Types.ARRAY:
                return java.sql.Array.class;
            case Types.DATALINK:
                return java.net.URL.class;
            case Types.DISTINCT:
            case Types.JAVA_OBJECT:
                return Object.class;
            case Types.NULL:
            case Types.OTHER:
            case Types.REF:
                return java.sql.Ref.class;
            case Types.STRUCT:
                return java.sql.Struct.class;
            default:
                logger.warn("Unsupported jdbc type: {}", jdbcType);
                return null;
        }
    }

    public static Class<? extends Number> getDefaultIntegerType(long precision) {
        if(precision < Math.log10(Integer.MAX_VALUE)) {
            return Integer.class;
        } else if(precision < Math.log10(Long.MAX_VALUE)) {
            return Long.class;
        } else {
            return BigInteger.class;
        }
    }


    public boolean isAutoincrement() {
        return autoincrement;
    }

    public Long getMaximumPrecision() {
        return maximumPrecision;
    }

    public String getLiteralPrefix() {
        return literalPrefix;
    }

    public String getLiteralSuffix() {
        return literalSuffix;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isSearchable() {
        return searchable;
    }

    public short getMinimumScale() {
        return minimumScale;
    }

    public short getMaximumScale() {
        return maximumScale;
    }

    @Override
    public String toString() {
        return "Type{" +
                "typeName='" + typeName + '\'' +
                ", jdbcType=" + jdbcType +
                ", autoincrement=" + autoincrement +
                ", maximumPrecision=" + maximumPrecision +
                ", literalPrefix='" + literalPrefix + '\'' +
                ", literalSuffix='" + literalSuffix + '\'' +
                ", nullable=" + nullable +
                ", caseSensitive=" + caseSensitive +
                ", searchable=" + searchable +
                ", minimumScale=" + minimumScale +
                ", maximumScale=" + maximumScale +
                '}';
    }

    public boolean isPrecisionRequired() {
        if (precisionRequired!= null){
            return precisionRequired;
        }
        switch (jdbcType) {
            case Types.BIGINT:
                return false;
            case Types.BIT:
                return false;
            case Types.BOOLEAN:
                return false;
            case Types.CHAR:
                return true;
            case Types.VARCHAR:
                return true;
            case Types.DATE:
                return false;
            case Types.TIME:
                return false;
            case Types.TIMESTAMP:
                return false;
            case Types.DECIMAL:
                return true;
            case Types.NUMERIC:
                return true;
            case Types.DOUBLE:
                return false;
            case Types.REAL:
                return false;
            case Types.FLOAT:
                return false;
            case Types.INTEGER:
                return false;
            case Types.SMALLINT:
                return false;
            case Types.TINYINT:
                return false;
            case Types.BINARY:
                return false;
            case Types.BLOB:
                 return false;
            case Types.CLOB:
                return false;
            case Types.LONGVARBINARY:
                return false;
            case Types.LONGVARCHAR:
                return false;
            case Types.VARBINARY:
                return false;
            case Types.ARRAY:
                return false;
            case Types.DATALINK:
                return false;
            case Types.DISTINCT:
            case Types.JAVA_OBJECT:
                return false;
            case Types.NULL:
            case Types.OTHER:
            case Types.REF:
                return false;
            case Types.STRUCT:
                return false;
            default:
                return false;
        }

    }

    public boolean isScaleRequired() {
        if (scaleRequired!= null){
            return scaleRequired;
        }
        switch (jdbcType) {
            case Types.BIGINT:
                return false;
            case Types.BIT:
                return false;
            case Types.BOOLEAN:
                return false;
            case Types.CHAR:
                return false;
            case Types.VARCHAR:
                return false;
            case Types.DATE:
                return false;
            case Types.TIME:
                return false;
            case Types.TIMESTAMP:
                return false;
            case Types.DECIMAL:
                return true;
            case Types.NUMERIC:
                return true;
            case Types.DOUBLE:
                return false;
            case Types.REAL:
                return false;
            case Types.FLOAT:
                return false;
            case Types.INTEGER:
                return false;
            case Types.SMALLINT:
                return false;
            case Types.TINYINT:
                return false;
            case Types.BINARY:
                return false;
            case Types.BLOB:
                 return false;
            case Types.CLOB:
                return false;
            case Types.LONGVARBINARY:
                return false;
            case Types.LONGVARCHAR:
                return false;
            case Types.VARBINARY:
                return false;
            case Types.ARRAY:
                return false;
            case Types.DATALINK:
                return false;
            case Types.DISTINCT:
            case Types.JAVA_OBJECT:
                return false;
            case Types.NULL:
            case Types.OTHER:
            case Types.REF:
                return false;
            case Types.STRUCT:
                return false;
            default:
                return false;
        }
    }

    public boolean isNumeric() {
        switch (jdbcType) {
            case Types.NUMERIC:
            case Types.DECIMAL:
            case Types.INTEGER:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.BIGINT:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.REAL:
                return true;
            default: return false;
        }
    }

}
