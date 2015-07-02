<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://di.tamu.edu/DRI/1.0/"
                xmlns:dri="http://di.tamu.edu/DRI/1.0/"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:i18n="http://apache.org/cocoon/i18n/2.1"
                exclude-result-prefixes="xsl dri i18n">

  <xsl:output indent="yes"/>            

   <xsl:param name="qualifier" />
   <xsl:param name="contextPath" />
   <xsl:param name="relativePath" />
   <xsl:variable name="vLower" select="'abcdefghijklmnopqrstuvwxyz'"/>
   <xsl:variable name="vUpper" select="'ABCDEFGHIJKLMNOPQRSTUVWXYZ'"/>
   <xsl:variable name="trailTrace" select="''" />

   <xsl:variable name="context">
      <xsl:call-template name="GetLastSegment">
         <xsl:with-param name="value" select="$contextPath" />
         <xsl:with-param name="separator" select="'/'" />
      </xsl:call-template>
   </xsl:variable>
   <xsl:template name="GetLastSegment">
      <xsl:param name="value" />
      <xsl:param name="separator" select="'.'" />
      <xsl:choose>
         <xsl:when test="contains($value, $separator)">
            <xsl:call-template name="GetLastSegment">
               <xsl:with-param name="value" select="substring-after($value, $separator)" />
               <xsl:with-param name="separator" select="$separator" />
            </xsl:call-template>
         </xsl:when>
         <xsl:otherwise>
            <xsl:value-of select="$value" />
         </xsl:otherwise>
      </xsl:choose>
   </xsl:template>

   <!-- Identity template, copies everything as is -->
   <xsl:template match="@*|node()">
      <xsl:copy>
         <xsl:apply-templates select="@*|node()" />
      </xsl:copy>
   </xsl:template>

   <!-- Override for target element -->
   <xsl:template match="dri:pageMeta">
      <!-- Copy the element -->
      <xsl:copy>
         <!-- Add breadcrumb trail -->
         <trail target="/{$context}/">
            <i18n:text catalogue="default">xmlui.general.dspace_home</i18n:text>
         </trail>
         <xsl:call-template name="path-trail">
            <xsl:with-param name="relPath" select="$relativePath" />
            <xsl:with-param name="prefix" select="''" />
         </xsl:call-template>
         <trail>
            <xsl:copy-of select="dri:metadata[@element='title']" />
         </trail>
         <!-- Add contextapth -->
         <metadata element="request" qualifier="URI"><xsl:value-of select="$qualifier"/>/<xsl:value-of select="$relativePath"/></metadata>
         <metadata element="qualifier"><xsl:value-of select="$qualifier" /></metadata>

         <!-- copy everything other than the tags processed above -->
         <xsl:apply-templates select="*[not(self::trail or self::dri:metadata[@element='contextPath'])]" />
      </xsl:copy>
   </xsl:template>

   <xsl:template name="path-trail">
      <xsl:param name="relPath" />
      <xsl:param name="prefix" />
      <xsl:if test="contains($relPath, '/')">
         <xsl:variable name="trail" select="substring-before($relPath, '/')" />
         <xsl:variable name="trailPath" select="concat($prefix, $trail, '/')" />
         <trail target="/{$context}/{$qualifier}/{$trailPath}">
            <i18n:text catalogue="default"><xsl:value-of select="concat(translate(substring($trail,1,1), $vLower, $vUpper), substring($trail,2))" /></i18n:text>
         </trail>
         <xsl:call-template name="path-trail">
            <xsl:with-param name="relPath" select="substring-after($relPath, '/')" />
            <xsl:with-param name="prefix" select="$trailPath" />
         </xsl:call-template>
      </xsl:if>
   </xsl:template>
</xsl:stylesheet>