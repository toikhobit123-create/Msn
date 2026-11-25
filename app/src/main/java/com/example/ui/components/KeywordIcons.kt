package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Keyword
import com.example.model.UnitOnBoard

@DrawableRes
fun keywordIconRes(keyword: Keyword): Int = when (keyword) {
    Keyword.AUGMENT -> R.drawable.ic_kw_augment
    Keyword.BARRIER -> R.drawable.ic_kw_barrier
    Keyword.BRASH -> R.drawable.ic_kw_brash
    Keyword.CHALLENGER -> R.drawable.ic_kw_challenger
    Keyword.DEEP -> R.drawable.ic_kw_deep
    Keyword.DOUBLE_ATTACK -> R.drawable.ic_kw_double_attack
    Keyword.ELUSIVE -> R.drawable.ic_kw_elusive
    Keyword.EVOLVE -> R.drawable.ic_kw_evolve
    Keyword.FATED -> R.drawable.ic_kw_fated
    Keyword.FEARSOME -> R.drawable.ic_kw_fearsome
    Keyword.FORMIDABLE -> R.drawable.ic_kw_formidable
    Keyword.FURY -> R.drawable.ic_kw_fury
    Keyword.IMPACT -> R.drawable.ic_kw_impact
    Keyword.LIFESTEAL -> R.drawable.ic_kw_lifesteal
    Keyword.LURK -> R.drawable.ic_kw_lurk
    Keyword.OVERWHELM -> R.drawable.ic_kw_overwhelm
    Keyword.QUICK_ATTACK -> R.drawable.ic_kw_quick_attack
    Keyword.REGENERATION -> R.drawable.ic_kw_regeneration
    Keyword.SCOUT -> R.drawable.ic_kw_scout
    Keyword.SPELLSHIELD -> R.drawable.ic_kw_spellshield
    Keyword.TOUGH -> R.drawable.ic_kw_tough
    Keyword.SHARPSIGHT -> R.drawable.ic_kw_sharpsight
    Keyword.SPIRIT -> R.drawable.ic_kw_spirit
    Keyword.DEATHLESS -> R.drawable.ic_kw_deathless
    Keyword.EPHEMERAL -> R.drawable.ic_kw_ephemeral
    Keyword.VULNERABLE -> R.drawable.ic_kw_vulnerable
    Keyword.STUN -> R.drawable.ic_kw_stun
    Keyword.FROSTBITE -> R.drawable.ic_kw_frostbite
    Keyword.SILENCE -> R.drawable.ic_kw_silence
    Keyword.GLOOM -> R.drawable.ic_kw_gloom
    Keyword.CANT_BLOCK -> R.drawable.ic_kw_cant_block
    Keyword.IMMOBILE -> R.drawable.ic_kw_immobile
    Keyword.SHACKLED -> R.drawable.ic_kw_shackled
    Keyword.ATTUNE -> R.drawable.ic_kw_attune
    Keyword.FLEETING -> R.drawable.ic_kw_fleeting
    Keyword.ELEMENTAL -> R.drawable.ic_kw_elemental
    Keyword.DAYBREAK -> R.drawable.ic_kw_daybreak
    Keyword.SUPPORT -> R.drawable.ic_kw_support
}

@Composable
fun KeywordIcon(
    keyword: Keyword,
    size: Dp = 16.dp,
    contentDescription: String? = keyword.displayName,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = keywordIconRes(keyword)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

fun resolveUnitDisplayKeywords(unit: UnitOnBoard): List<Keyword> = buildList {
    if (unit.hasBarrier) add(Keyword.BARRIER)
    if (unit.hasSpellShield) add(Keyword.SPELLSHIELD)
    if (unit.isFrostbitten) add(Keyword.FROSTBITE)
    if (unit.isStunned) add(Keyword.STUN)
    if (unit.isSilenced) add(Keyword.SILENCE)
    if (unit.isShackled) add(Keyword.SHACKLED)
    for (kw in unit.keywords) {
        if (!contains(kw)) add(kw)
    }
}
