package com.fueledbycaffeine.spotlight.idea.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.LexerBase
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Token types for Spotlight project files.
 */
object SpotlightProjectsTokenTypes {
  val LINE = IElementType("LINE", SpotlightProjectsLanguage)
  val COMMENT = IElementType("COMMENT", SpotlightProjectsLanguage)
  val NEWLINE = IElementType("NEWLINE", SpotlightProjectsLanguage)
}

/**
 * Lexer that tokenizes each line separately, enabling per-line navigation highlighting.
 */
class SpotlightProjectsLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var bufferEnd: Int = 0
  private var tokenStart: Int = 0
  private var tokenEnd: Int = 0
  private var tokenType: IElementType? = null

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.bufferEnd = endOffset
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    advance()
  }

  override fun getState(): Int = 0

  override fun getTokenType(): IElementType? = tokenType

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun advance() {
    tokenStart = tokenEnd
    
    if (tokenStart >= bufferEnd) {
      tokenType = null
      return
    }
    
    // Find the end of the current line (or end of buffer)
    var pos = tokenStart
    
    // Check if we're at a newline
    if (buffer[pos] == '\n') {
      tokenEnd = pos + 1
      tokenType = SpotlightProjectsTokenTypes.NEWLINE
      return
    }
    
    // Find end of line content
    while (pos < bufferEnd && buffer[pos] != '\n') {
      pos++
    }
    
    tokenEnd = pos
    
    // Determine token type based on content
    val lineContent = buffer.subSequence(tokenStart, tokenEnd).toString().trim()
    tokenType = if (lineContent.startsWith("#")) {
      SpotlightProjectsTokenTypes.COMMENT
    } else {
      SpotlightProjectsTokenTypes.LINE
    }
  }

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = bufferEnd
}

/**
 * Parser definition for Spotlight project files.
 * Creates separate PSI elements for each line to enable per-line navigation highlighting.
 */
class SpotlightProjectsParserDefinition : ParserDefinition {
  
  companion object {
    val FILE = IFileElementType(SpotlightProjectsLanguage)
    val COMMENTS = TokenSet.create(SpotlightProjectsTokenTypes.COMMENT)
    val WHITESPACE = TokenSet.create(SpotlightProjectsTokenTypes.NEWLINE)
  }
  
  override fun createLexer(project: Project?): Lexer = SpotlightProjectsLexer()
  
  override fun createParser(project: Project?): PsiParser = PsiParser { _, builder ->
    val marker = builder.mark()
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    marker.done(FILE)
    builder.treeBuilt
  }
  
  override fun getFileNodeType(): IFileElementType = FILE
  
  override fun getCommentTokens(): TokenSet = COMMENTS
  
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  
  override fun getWhitespaceTokens(): TokenSet = WHITESPACE
  
  override fun createElement(node: ASTNode): PsiElement {
    return ASTWrapperPsiElement(node)
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
