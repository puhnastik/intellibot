package com.millennialmedia.intellibot.psi.element;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.millennialmedia.intellibot.psi.RobotTokenTypes;
import com.millennialmedia.intellibot.psi.dto.ImportType;
import com.millennialmedia.intellibot.psi.ref.PythonResolver;
import com.millennialmedia.intellibot.psi.ref.RobotPythonClass;
import com.millennialmedia.intellibot.psi.ref.RobotPythonFile;
import com.millennialmedia.intellibot.psi.util.PerformanceCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Stephen Abrams
 */
public class HeadingImpl extends RobotPsiElementBase implements Heading {

    private static final String ROBOT_BUILT_IN = "BuiltIn";

    private Collection<KeywordInvokable> invokedKeywords;
    private Collection<DefinedKeyword> definedKeywords;
    private Collection<KeywordDefinition> testCases;
    private Collection<KeywordFile> keywordFiles;
    private Collection<PsiFile> referencedFiles;
    private Collection<DefinedVariable> declaredVariables;

    public HeadingImpl(@NotNull final ASTNode node) {
        super(node, RobotTokenTypes.HEADING);
    }

    @Override
    public boolean isSettings() {
        // TODO: better OO
        String text = getTextData();
        return text != null && text.startsWith("*** Setting");
    }

    public boolean containsVariables() {
        String text = getText();
        return text != null && text.startsWith("*** Variable");
    }

    @Override
    public boolean containsTestCases() {
        // TODO: better OO
        String text = getTextData();
        return text != null && text.startsWith("*** Test Case");
    }

    @Override
    public boolean containsKeywordDefinitions() {
        // TODO: better OO
        String text = getTextData();
        return text != null && (text.startsWith("*** Keyword") || text.startsWith("*** User Keyword"));
    }

    private boolean containsImports() {
        return isSettings();
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        if (isSettings()) {
            PsiFile file = getContainingFile();
            if (file instanceof RobotFile) {
                ((RobotFile) file).importsChanged();
            }
        }
        this.definedKeywords = null;
        this.keywordFiles = null;
        this.invokedKeywords = null;
        this.referencedFiles = null;
        this.testCases = null;
        this.declaredVariables = null;
    }

    @Override
    public void importsChanged() {
        this.definedKeywords = null;
        this.keywordFiles = null;
        this.invokedKeywords = null;
        this.referencedFiles = null;
        this.testCases = null;
        this.declaredVariables = null;
    }

    @NotNull
    @Override
    public Collection<DefinedVariable> getDefinedVariables() {
        Collection<DefinedVariable> results = this.declaredVariables;
        if (results == null) {
            PerformanceCollector debug = new PerformanceCollector(this, "defined variables");
            results = collectVariables();
            this.declaredVariables = results;
            debug.complete();
        }
        return results;
    }

    @NotNull
    Collection<DefinedVariable> collectVariables() {
        if (containsVariables()) {
            List<DefinedVariable> results = new ArrayList<DefinedVariable>();
            for (PsiElement child : getChildren()) {
                if (child instanceof DefinedVariable) {
                    results.add((DefinedVariable) child);
                }
            }
            return results;
        } else if (containsImports()) {
            List<DefinedVariable> results = new ArrayList<DefinedVariable>();
            for (KeywordFile imported : getImportedFiles()) {
                if (imported.getImportType() == ImportType.VARIABLES) {
                    results.addAll(imported.getDefinedVariables());
                }
            }
            return results;
        } else {
            return Collections.emptySet();
        }
    }

    @NotNull
    private Collection<KeywordDefinition> getTestCases() {
        Collection<KeywordDefinition> results = this.testCases;
        if (results == null) {
            PerformanceCollector debug = new PerformanceCollector(this, "test cases");
            results = collectTestCases();
            this.testCases = results;
            debug.complete();
        }
        return results;
    }

    @NotNull
    private Collection<KeywordDefinition> collectTestCases() {
        if (!containsTestCases()) {
            return Collections.emptySet();
        }
        List<KeywordDefinition> results = new ArrayList<KeywordDefinition>();
        for (PsiElement child : getChildren()) {
            if (child instanceof KeywordDefinition) {
                results.add(((KeywordDefinition) child));
            }
        }
        return results;
    }

    @NotNull
    @Override
    public Collection<DefinedKeyword> getDefinedKeywords() {
        Collection<DefinedKeyword> results = this.definedKeywords;
        if (results == null) {
            PerformanceCollector debug = new PerformanceCollector(this, "defined keywords");
            results = collectDefinedKeywords();
            this.definedKeywords = results;
            debug.complete();
        }
        return results;
    }

    @NotNull
    private Collection<DefinedKeyword> collectDefinedKeywords() {
        if (!containsKeywordDefinitions()) {
            return Collections.emptySet();
        }
        List<DefinedKeyword> results = new ArrayList<DefinedKeyword>();
        for (PsiElement child : getChildren()) {
            if (child instanceof DefinedKeyword) {
                results.add(((DefinedKeyword) child));
            }
        }
        return results;
    }

    @NotNull
    @Override
    public Collection<PsiFile> getFilesFromInvokedKeywords() {
        Collection<PsiFile> results = this.referencedFiles;
        if (results == null) {
            PerformanceCollector debug = new PerformanceCollector(this, "files from invoked keywords");
            results = collectReferencedFiles();
            this.referencedFiles = results;
            debug.complete();
        }
        return results;
    }

    @NotNull
    private Collection<PsiFile> collectReferencedFiles() {
        Collection<PsiFile> results = new HashSet<PsiFile>();
        for (KeywordInvokable keyword : getInvokedKeywords()) {
            PsiReference reference = keyword.getReference();
            if (reference != null) {
                PsiElement resolved = reference.resolve();
                if (resolved != null) {
                    results.add(resolved.getContainingFile());
                }
            }
            addReferencedArguments(results, keyword);
        }
        return results;
    }

    private void addReferencedArguments(@NotNull Collection<PsiFile> results, @NotNull KeywordInvokable keyword) {
        for (Argument argument : keyword.getArguments()) {
            PsiReference reference = argument.getReference();
            if (reference != null) {
                PsiElement resolved = reference.resolve();
                if (resolved != null) {
                    results.add(resolved.getContainingFile());
                }
            }
        }
    }

    @NotNull
    private Collection<KeywordInvokable> getInvokedKeywords() {
        Collection<KeywordInvokable> results = this.invokedKeywords;
        if (results == null) {
            PerformanceCollector debug = new PerformanceCollector(this, "invoked keywords");
            results = collectInvokedKeywords();
            this.invokedKeywords = results;
            debug.complete();
        }
        return results;
    }

    @NotNull
    private Collection<KeywordInvokable> collectInvokedKeywords() {
        List<KeywordInvokable> results = new ArrayList<KeywordInvokable>();
        for (PsiElement child : getChildren()) {
            if (child instanceof KeywordStatement) {
                for (PsiElement statement : child.getChildren()) {
                    if (statement instanceof KeywordInvokable) {
                        results.add((KeywordInvokable) statement);
                    }
                }
            }
        }
        for (KeywordDefinition testCase : getTestCases()) {
            results.addAll(testCase.getInvokedKeywords());
        }
        for (DefinedKeyword definedKeyword : getDefinedKeywords()) {
            results.addAll(definedKeyword.getInvokedKeywords());
        }
        return results;
    }

    @NotNull
    @Override
    public Collection<KeywordFile> getImportedFiles() {
        Collection<KeywordFile> results = this.keywordFiles;
        if (results == null) {
            PerformanceCollector debug = new PerformanceCollector(this, "imported files");
            results = collectImportFiles();
            this.keywordFiles = results;
            debug.complete();
        }
        return results;
    }

    private Collection<KeywordFile> collectImportFiles() {
        if (!containsImports()) {
            return Collections.emptySet();
        }
        List<KeywordFile> files = new ArrayList<KeywordFile>();
        for (PsiElement child : getChildren()) {
            if (child instanceof Import) {
                Import imp = (Import) child;
                if (imp.isResource()) {
                    Argument argument = PsiTreeUtil.findChildOfType(imp, Argument.class);
                    if (argument != null) {
                        PsiElement resolution = resolveImport(argument);
                        if (resolution instanceof KeywordFile) {
                            files.add((KeywordFile) resolution);
                        }
                    }
                } else if (imp.isLibrary() || imp.isVariables()) {
                    Argument argument = PsiTreeUtil.findChildOfType(imp, Argument.class);
                    if (argument != null) {
                        PsiElement resolved = resolveImport(argument);
                        PyClass resolution = PythonResolver.castClass(resolved);
                        if (resolution != null) {
                            files.add(new RobotPythonClass(argument.getPresentableText(), resolution,
                                    ImportType.getType(imp.getPresentableText())));
                        }
                        PyFile file = PythonResolver.castFile(resolved);
                        if (file != null) {
                            files.add(new RobotPythonFile(argument.getPresentableText(), file,
                                    ImportType.getType(imp.getPresentableText())));
                        }
                    }
                }
            }
        }
        addBuiltIn(files);
        return files;
    }

    private void addBuiltIn(List<KeywordFile> files) {
        PyClass builtIn = PythonResolver.findClass(ROBOT_BUILT_IN, getProject());
        if (builtIn != null) {
            files.add(new RobotPythonClass(ROBOT_BUILT_IN, builtIn, ImportType.LIBRARY));
        }
    }

    @Nullable
    private PsiElement resolveImport(@NotNull Argument argument) {
        PsiReference reference = argument.getReference();
        if (reference != null) {
            return reference.resolve();
        }
        return null;
    }
}