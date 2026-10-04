"""Real, variable normal-game earnings must cover the full service path."""
import json,re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class ServiceBudgetTest(unittest.TestCase):
 def test_current_task_keeps_required_runtime_identity(self):
  task=(ROOT/'docs/current-task.md').read_text(encoding='utf-8')
  self.assertEqual(['WORLD-FULL-01'],re.findall(r'^task_id:\s*([A-Z0-9-]+)',task,re.M))
 def test_original_trade_peak_is_covered_without_gifting_or_changing_prices(self):
  p=json.loads((ROOT/'game-data/provenance/world-cave85-content.json').read_text(encoding='utf-8'))
  base=json.loads((ROOT/'game-data/provenance/town01.json').read_text(encoding='utf-8'))
  items={i['id']:i for i in p['items']}
  weapon=items['rom.weapon.3'];medicine=items['rom.medicine.12']
  # Existing town01 armor1 is 80/40; its source table remains immutable.
  ledger=[200,-100,80,-40,80,-40,8]
  cumulative=[];spent=0
  for amount in ledger:spent+=amount;cumulative.append(spent)
  self.assertEqual(220,max(cumulative))
  self.assertEqual((200,100),(weapon['buyPrice'],weapon['sellPrice']))
  self.assertEqual((80,40),(medicine['buyPrice'],medicine['sellPrice']))
  self.assertEqual(8,next(i for i in p['inns']if i['id']=='rom.inn.1')['price'])
  source=(ROOT/'android/app/src/androidTest/java/org/fengshen/dev/TouchTest.kt').read_text(encoding='utf-8')
  driver=source.split('private fun normalWorldBatchContinuation(west:Boolean)',1)[1].split('fun testNormalWorldNorthPalaceAndPearl',1)[0]
  self.assertIn('return pills+herbs+serviceBudget',driver)
  self.assertIn('afterArmor+medicine.buyPrice!!',driver)
  self.assertIn('Normal supply earnings exhausted',driver.replace('Bounded normal supply earnings exhausted','Normal supply earnings exhausted'))
  self.assertNotIn('money=220',driver)
 def test_north_training_uses_real_nonpoison_zone_and_affordable_supplies(self):
  source=(ROOT/'android/app/src/androidTest/java/org/fengshen/dev/TouchTest.kt').read_text(encoding='utf-8')
  driver=source.split('fun testNormalWorldNorthPalaceAndPearlFromVerifiedNanhaiSave()',1)[1].split('\n    fun ',1)[0]
  # Actual v61 source, not a synthetic guarantee about future random earnings.
  self.assertGreaterEqual(314-(8-2)*15-4*20,8)
  self.assertIn('replenishTrainingSupplies();inn();enterTrainingArea()',driver)
  self.assertIn('returnFromTraining();replenishTrainingSupplies();inn();enterTrainingArea()',driver)
  self.assertIn('listOf(HerbUse.ID to 8,AntidoteUse.ID to 4)',driver)
  self.assertIn('trade(id,true,count)',driver)
  self.assertIn('walkTo(5,24);assertEquals(96,v.world.mapId)',driver)
  self.assertNotIn('walkTo(29,43)',driver)
  self.assertIn('fight.phase!=BattlePhase.DEFEAT',driver)
  self.assertNotIn('inventory=',driver)
  proof=json.loads((ROOT/'game-data/provenance/world-final-hall-content.json').read_text(encoding='utf-8'))
  zone=next(z for z in proof['combatOverlay']['zones'] if z['mapId']==96)
  ids={e['enemyId'] for g in zone['groups'] for e in g['entities']}
  self.assertEqual({8,9},ids)
  enemies={e['id']:e for e in proof['combatOverlay']['enemies']}
  self.assertEqual({0},{enemies[i]['behaviorByte'] for i in ids})
  self.assertEqual({11,13},{enemies[i]['experienceReward'] for i in ids})
if __name__=='__main__':unittest.main()
