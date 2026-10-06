package org.fengshen.dev
import org.junit.Test
import org.junit.Assert.*
/** Isolated c50 QA-source policy comparison, not a normal App route or state grant.
 * Source: run37364795394, hall-batch checkpoint source SHA522023945623941247150a5e0525910bce332372b7489647459a07d526b3d48c.
 * Same c50 bosses/rules; only the fixture's preparation target differs. Real
 * instrumentation earns that target through actual encounters, shop and inn.
 */
class HallRoutePreparationTest {
 @Test fun isolatedOriginalTenHallTailRejectsInsufficientPreparationAndRetainsLivingPartyWithMargin() {
val source=listOf(CharacterState("nezha",25,28070,584,601,0,102,50,30,16,0,EquipmentState(4,-1,3,28)),CharacterState("xiaolongnv",26,26710,504,538,109,80,40,46,85,109,EquipmentState(19,-1,11,38)))
val growth=mapOf("nezha" to listOf(GrowthRow(26,31977,66,0,8,4,2,0,false),GrowthRow(27,36710,70,0,9,5,2,1,false),GrowthRow(28,41997,75,0,9,5,2,0,false),GrowthRow(29,47891,81,0,10,5,2,1,false),GrowthRow(30,54452,86,0,11,6,2,0,false),GrowthRow(31,61740,92,0,11,6,3,1,false),GrowthRow(32,69825,97,0,12,7,3,0,false),GrowthRow(33,78779,103,0,13,7,3,1,false),GrowthRow(34,88683,110,0,13,7,3,0,false)),"xiaolongnv" to listOf(GrowthRow(26,25664,52,6,6,3,2,3,false),GrowthRow(27,29507,56,6,7,3,2,3,false),GrowthRow(28,33815,60,6,7,4,2,3,false),GrowthRow(29,38636,65,6,8,4,3,3,false),GrowthRow(30,44021,69,7,8,4,3,3,false),GrowthRow(31,50028,73,7,9,5,3,3,false),GrowthRow(32,56718,78,7,10,5,3,3,false),GrowthRow(33,64159,83,7,10,5,3,3,false),GrowthRow(34,72426,87,7,11,6,3,3,false)))
val defs=listOf(EnemyDefinition(144,"fixture-144",850,68,44,320,400,217,0,null).also{it.specialBaseDamage=null},EnemyDefinition(145,"fixture-145",1150,76,48,350,450,217,8,null).also{it.specialBaseDamage=null},EnemyDefinition(146,"fixture-146",1300,80,56,400,500,217,9,null).also{it.specialBaseDamage=null},EnemyDefinition(147,"fixture-147",1540,90,63,460,550,243,2,null).also{it.specialBaseDamage=42},EnemyDefinition(148,"fixture-148",1860,100,70,540,620,230,0,null).also{it.specialBaseDamage=null},EnemyDefinition(149,"fixture-149",2100,110,75,600,680,204,0,null).also{it.specialBaseDamage=null},EnemyDefinition(150,"fixture-150",2500,120,78,700,770,204,3,40).also{it.specialBaseDamage=null},EnemyDefinition(151,"fixture-151",3500,130,80,750,820,230,8,null).also{it.specialBaseDamage=null}).associateBy{it.id}
val hits=mapOf(-1 to 64,0 to 54,1 to 54,18 to 44,19 to 51,2 to 51,21 to 51,3 to 51,33 to 51,4 to 54,5 to 51,6 to 51)
val physical=PhysicalRules(hits,listOf(242,230,179,128,76,51,25,12,12,9,7,2,255,255,255,243,229,217,153,88,37,24,24,12,0,0,0,255,255,255,255,241,165,100,75,24))
val own=PhysicalRules(hits,listOf(242,230,206,153,102,76,51,25,12,12,9,2,255,255,255,255,242,229,179,127,63,37,24,19,0,0,0,0,255,255,255,255,216,139,75,57))
val agility=mapOf(1 to 1,10 to 6,11 to 7,12 to 7,13 to 8,137 to 8,138 to 12,139 to 17,14 to 9,140 to 30,141 to 25,142 to 32,143 to 34,144 to 35,145 to 40,146 to 41,147 to 38,148 to 43,149 to 46,15 to 8,150 to 46,151 to 48,152 to 62,153 to 59,154 to 56,155 to 58,156 to 96,157 to 110,16 to 10,17 to 11,18 to 13,19 to 13,2 to 2,20 to 13,21 to 15,22 to 16,23 to 16,24 to 18,25 to 18,26 to 18,27 to 22,28 to 23,29 to 22,3 to 2,30 to 21,31 to 24,32 to 25,33 to 26,34 to 27,35 to 28,36 to 31,37 to 27,38 to 31,39 to 29,4 to 3,40 to 33,41 to 31,42 to 35,43 to 33,44 to 37,45 to 41,46 to 39,47 to 40,5 to 4,50 to 48,51 to 41,52 to 46,53 to 48,56 to 53,57 to 45,58 to 58,59 to 56,6 to 4,7 to 5,8 to 5,9 to 6)
val bonuses=mapOf(0 to 2,1 to 5,2 to 10,3 to 16,19 to 20,18 to 12,4 to 25,5 to 38,6 to 54,21 to 46,33 to 70,7 to 75)
val armors=mapOf(0 to 2,1 to 6,2 to 12,11 to 20,3 to 20,10 to 12,4 to 35,12 to 35,18 to 50)
val herb=ItemDefinition(HerbUse.ID,"藥草",null,"c50",category="medicine",originalId=0,herbUse=HerbUseDefinition(50,true,"c50"))
for(target in listOf(25,32)) {
 var survived=0;var minimum=99999
 for(seed in 0 until 1000) {
  val random=java.util.Random(seed.toLong());var qty=8;var alive=true
  var party=source.map{p->var a=p;for(r in growth.getValue(p.id).filter{it.level>p.level&&it.level<=target})a=a.copy(level=r.level,experience=r.threshold,hp=a.hp+r.hp,maxHp=a.maxHp+r.hp,mp=a.mp+r.mp,maxMp=a.maxMp!!+r.mp,strength=a.strength+r.strength,stamina=a.stamina+r.stamina,agility=a.agility+r.agility,spirit=a.spirit+r.spirit);a}
  for(eid in 144..151) {
   if(eid==145)qty=minOf(10,qty+1)
   if(eid==147)party=party.map{if(it.id=="nezha")it.copy(equipment=it.equipment!!.copy(rightHand=5))else it}
   // Isolation omits natural encounters; keep a clearly labelled 30HP travel margin per actor per hall.
   party=party.map{it.copy(hp=(it.hp-30).coerceAtLeast(0))}
   if(party.any{it.hp==0}){alive=false;break}
   val group=EncounterGroup(eid,listOf(EncounterMember(3,eid)))
   val content=BattleContent(23,emptyList(),listOf(group),defs,growth.getValue("nezha"),0,6,50,16,enemyAgility=agility,escapeEnabled=true,physicalRules=physical).also{it.characterPhysicalRules=mapOf("xiaolongnv" to own);it.characterGrowth=mapOf("xiaolongnv" to growth.getValue("xiaolongnv"))}
   val b=OpeningBattle(group,content,party.first(),bonuses.getValue(party.first().equipment!!.rightHand),armors.getValue(party.first().equipment!!.body)).also{it.configureParty(party,mapOf("nezha" to 0,"xiaolongnv" to 1),party.associate{p->p.id to bonuses.getValue(p.equipment!!.rightHand)},party.associate{p->p.id to armors.getValue(p.equipment!!.body)})}
   var rounds=0
   while(b.phase==BattlePhase.TARGET&&rounds++<1000) {
    val a=b.inputHero
    if(a==null)b.continueSkippedCommands{random.nextInt(256)}
    else if(a.hp<=a.maxHp/2&&qty>b.herbsConsumed)b.useHerb(a.id,qty,herb){random.nextInt(256)}
    else b.attack(3){random.nextInt(256)}
   }
   qty-=b.herbsConsumed
   if(b.phase!=BattlePhase.VICTORY||b.party.any{it.hp==0}){alive=false;break}
   b.settle(0);party=b.charactersAfterBattle()
  }
  if(alive){survived++;minimum=minOf(minimum,party.minOf{it.hp})}
 }
 println("ISOLATED_POLICY level=$target survived=$survived/1000 minimumFinalHP=$minimum; NOT_NORMAL_APP")
 if(target==25)assertEquals("Reproduce legal partial-party loss; do not delete the App alive assertion",0,survived)
 else {assertEquals("Seeded boundary comparison, not normal route acceptance",1000,survived);assertTrue(minimum>=250)}
}
 }
}
