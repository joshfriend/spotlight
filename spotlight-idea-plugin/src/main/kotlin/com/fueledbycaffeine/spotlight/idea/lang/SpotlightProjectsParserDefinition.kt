package com.fueledbycaffeine.spotlight.idea.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Minimal parser definition for Spotlight project files.
 * Treats the file as plain text but enables language features like commenting.
 */
class SpotlightProjectsParserDefinition : ParserDefinition {
  
  companion object {
    val FILE = IFileElementType(SpotlightProjectsLanguage)
  }
  
  override fun createLexer(project: Project?): Lexer = EmptyLexer()
  
  override fun createParser(project: Project?): PsiParser = PsiParser { _, builder ->
    val marker = builder.mark()
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    marker.done(FILE)
    builder.treeBuilt
  }
  
  override fun getFileNodeType(): IFileElementType = FILE
  
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
  
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  
  override fun createElement(node: ASTNode): PsiElement {
    throw UnsupportedOperationException("Not supported for plain text files")
  }
  
  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return SpotlightProjectsFile(viewProvider)
  }
}

/**
 * PsiFile implementation for Spotlight project files.
 * Associates the file with the SpotlightProjects language and file type.
 */
class SpotlightProjectsFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, SpotlightProjectsLanguage) {
  override fun getFileType(): FileType = SpotlightProjectsFileType.INSTANCE
}
