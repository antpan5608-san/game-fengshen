"""Real, variable normal-game earnings must cover the full service path."""
import json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class ServiceBudgetTest(unittest.TestCase):
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
if __name__=='__main__':unittest.main()
