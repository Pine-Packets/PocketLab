package com.pineandpackets.pocketlab.engine.rules

import com.pineandpackets.pocketlab.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * Declarative rule model that can be loaded from YAML/JSON files.
 * Rules are data-driven and evaluated against facts extracted from APKs.
 */
@Serializable
data class Rule(
    val id: String,
    val title: String,
    val category: String,
    val severity: String,
    val confidence: String = "HIGH",
    val description: String,
    val analystDescription: String? = null,
    val whenClause: WhenClause,
    val unlessClause: UnlessClause? = null,
    val evidence: List<EvidenceTemplate> = emptyList(),
    val limitations: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val enabled: Boolean = true
)

@Serializable
data class WhenClause(
    val all: List<Condition>? = null,
    val any: List<Condition>? = null
)

@Serializable
data class UnlessClause(
    val any: List<Condition>? = null,
    val all: List<Condition>? = null
)

@Serializable
data class Condition(
    val fact: String? = null,
    val value: String? = null,
    val contains: String? = null,
    val matches: String? = null,
    val all: List<Condition>? = null,
    val any: List<Condition>? = null
)

@Serializable
data class EvidenceTemplate(
    val type: String,
    val excerpt: String? = null,
    val excerptEncoding: String = "text"
)

/**
 * Interpreter for declarative rules.
 * Evaluates rules against a set of facts extracted from APK analysis.
 */
class RuleInterpreter {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Load rules from a YAML/JSON file.
     */
    fun loadRules(file: File): List<Rule> {
        return try {
            val content = file.readText()
            // For now, we'll use JSON format. YAML support can be added later.
            json.decodeFromString<List<Rule>>(content)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load rules from ${file.absolutePath}")
            emptyList()
        }
    }
    
    /**
     * Evaluate a list of rules against a set of facts.
     */
    fun evaluateRules(rules: List<Rule>, facts: Set<Fact>): List<Finding> {
        return rules.filter { it.enabled }
            .mapNotNull { rule -> evaluateRule(rule, facts) }
    }
    
    /**
     * Evaluate a single rule against a set of facts.
     */
    private fun evaluateRule(rule: Rule, facts: Set<Fact>): Finding? {
        // Check when clause
        val whenMatches = evaluateWhenClause(rule.whenClause, facts)
        if (!whenMatches) return null
        
        // Check unless clause (negation)
        if (rule.unlessClause != null) {
            val unlessMatches = evaluateUnlessClause(rule.unlessClause, facts)
            if (unlessMatches) return null
        }
        
        // Rule matched, create finding
        return createFinding(rule, facts)
    }
    
    /**
     * Evaluate a when clause (all/any conditions).
     */
    private fun evaluateWhenClause(whenClause: WhenClause, facts: Set<Fact>): Boolean {
        // If "all" is specified, all conditions must match
        if (whenClause.all != null) {
            return whenClause.all.all { condition -> evaluateCondition(condition, facts) }
        }
        
        // If "any" is specified, at least one condition must match
        if (whenClause.any != null) {
            return whenClause.any.any { condition -> evaluateCondition(condition, facts) }
        }
        
        return false
    }
    
    /**
     * Evaluate an unless clause (negation).
     */
    private fun evaluateUnlessClause(unlessClause: UnlessClause, facts: Set<Fact>): Boolean {
        // If "any" is specified, any condition matching means we should skip
        if (unlessClause.any != null) {
            return unlessClause.any.any { condition -> evaluateCondition(condition, facts) }
        }
        
        // If "all" is specified, all conditions must match
        if (unlessClause.all != null) {
            return unlessClause.all.all { condition -> evaluateCondition(condition, facts) }
        }
        
        return false
    }
    
    /**
     * Evaluate a single condition against facts. Conditions may be nested.
     */
    private fun evaluateCondition(condition: Condition, facts: Set<Fact>): Boolean {
        // Nested condition groups take precedence over leaf conditions
        if (condition.all != null) {
            return condition.all.all { evaluateCondition(it, facts) }
        }

        if (condition.any != null) {
            return condition.any.any { evaluateCondition(it, facts) }
        }

        val factType = condition.fact ?: return false
        val matchingFacts = facts.filter { it.type == factType }

        if (matchingFacts.isEmpty()) return false

        // If no value/contains/matches specified, just check if fact exists
        if (condition.value == null && condition.contains == null && condition.matches == null) {
            return true
        }

        // Check exact value match
        if (condition.value != null) {
            return matchingFacts.any { it.value == condition.value }
        }

        // Check contains match
        if (condition.contains != null) {
            return matchingFacts.any { it.value.contains(condition.contains, ignoreCase = true) }
        }

        // Check regex match
        if (condition.matches != null) {
            val regex = Regex(condition.matches, RegexOption.IGNORE_CASE)
            return matchingFacts.any { regex.matches(it.value) }
        }

        return false
    }
    
    /**
     * Create a Finding from a matched rule.
     */
    private fun createFinding(rule: Rule, facts: Set<Fact>): Finding {
        val matchingFacts = facts.filter { fact ->
            matchesAnyCondition(fact, rule.whenClause)
        }
        
        val evidence = if (rule.evidence.isNotEmpty()) {
            rule.evidence.map { template ->
                Evidence(
                    type = EvidenceType.valueOf(template.type),
                    excerpt = template.excerpt,
                    excerptEncoding = template.excerptEncoding
                )
            }
        } else {
            matchingFacts.map { fact ->
                Evidence(
                    type = EvidenceType.MANIFEST_DECLARATION,
                    excerpt = fact.value,
                    excerptEncoding = "text"
                )
            }
        }
        
        return Finding(
            id = java.util.UUID.randomUUID().toString(),
            ruleId = rule.id,
            title = rule.title,
            category = rule.category,
            severity = Severity.valueOf(rule.severity),
            confidence = Confidence.valueOf(rule.confidence),
            simpleExplanation = rule.description,
            analystExplanation = rule.analystDescription ?: rule.description,
            evidence = evidence,
            limitations = rule.limitations,
            recommendations = rule.recommendations
        )
    }
    
    /**
     * Check if a fact matches any condition in the when clause.
     * Nested condition groups are not considered for evidence matching.
     */
    private fun matchesAnyCondition(fact: Fact, whenClause: WhenClause): Boolean {
        val conditions = whenClause.all ?: whenClause.any ?: return false
        return conditions.any { condition ->
            condition.fact == fact.type &&
            (condition.value == null || condition.value == fact.value)
        }
    }
}

/**
 * Represents a fact extracted from APK analysis.
 */
data class Fact(
    val type: String,
    val value: String,
    val source: String? = null
)
