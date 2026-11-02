# ChoDaiDee + DouDiZhu Helper Plan

## 1\. Prepare

* Android use Jetpack Compose, ios use SwiftUI.
* Tech stack is KMP + google's tts engine + openCV + LiteRT + self-trained model

  * KMP for app framework
  * TTS engine for converting the detected results to the speech and output to user
  * openCV on Android and native equivalent on iOS for image pre-processing
  * LiteRT for model inference, for android, only optimize for qualcomm devices for now.
  * a self-trained model for identifying the card's suit and rank (see the following)

## 2\. App Layout

* This application uses the most native UI components on its supported platforms respectively.
* There are two pages at the moment: Home and Settings
* Home page use the camera capture as the base layer, and put nav bar and top bar as glossy layer on the top of the base layer. Can do island style if possible.
* Setting page is a simple list of settings, including the comparison rule for five-card hands, and the TTS engine settings.
* Android use material 3 design latest, specifically, use https://github.com/android/nowinandroid as the sample to copy-cat its components and designs.
* iOS use SwiftUI documentation to develop the application, and apply liquid glass as the native component and composition to the application.
* current progress: the pages are written, but it needs to follow this new plan to 1. apply the new requirements to the development and 2. check if all goals are reached.

## 3\. Rules

### Cho Dai Dee

* 52 cards, i.e., no jokers
* 4 players so each player has 13 cards
* suit from the most valuable to the least: Spade > Heart > Club > Diamond
* rank from the smallest to the biggest: 3, 4, 5, ..., K, A, 2
* rules for hands:

  * Single: 1 card
  * Pair: 2 cards of the same rank, the card with the higher suit is used for evaluating the pair
  * Trio: 3 cards of the same rank
  * straight: 5 cards in different ranks in consecutive order but cannot include the smallest rank and the biggest rank (e.g. A,2,3,4,5; Q,K,A,2,3). No limitation on the suit. Value of the hand is evaluated on the suit and rank of the card with the largest rank in the hand.
  * flush: 5 cards in the same suit, no limitation on the order. Value of the hand is evaluated on the suit and rank of the card with the largest rank in the hand.
  * full-house: Trio + Pair, the value of the hand is evaluated by the Trio.
  * four-of-a-kind: four cards of the same rank + a kicker. The value of the hand is evaluated by the quattro.
  * straight-flush: 5 cards in consecutive order AND in the same suit. the value of the hand is evaluated on the suit and rank of the card with the largest rank in the hand.
  * Comparison has two variations, user can adjust them in app settings:

    * Mainland: straight-flush > four-of-a-kind > full-house > flush > straight
    * Hong Kong: straight-flush > four-of-a-kind > full-house > straight > flush
  * Players can only follow the same pattern the previous player played unless he/she has the right-of-card, when playing with Single, Pair, and Trio.
  * Players can play any pattern when they have the right-of-card.
  * Players can play any five-card hand following the comparison rule when the previous player played five-card hand.
* this application is not responsible for playing strategy. It only helps to find out the collection of hands from the cards in his/her hands.
* The application is responsible for providing any available hands, even though one may have some cards sharing with another.

### Dou Di Zhu

* 54 cards including jokers
* 3 cards as Di Zhu's cards
* 3 players, thus peasants have 17 cards each, and Di Zhu (landlord) has 20 cards.
* suit and rank order are the same as Cho Dai Dee.
* rules for hands:

  * Single: 1 card
  * Pair: 2 cards of the same rank, the card with the higher suit is used for evaluating the pair.
  * Trio: 3 cards of the same rank
  * Straight: 5 or more cards in different ranks in consecutive rank order, but cannot include 2s and jokers. No limitation on the suit.
  * Sister Pairs: 3 or more pairs in consecutive rank order.
  * Airplane: 2 or more trios in consecutive rank order. Can be with or without wings. Wings can be single cards or pairs, and the number of wings must equal to the number of trios.
  * Trio with a kicker: Trio + single
  * Trio with a pair: Trio + pair
  * Quattro with kickers: Quattro + any 2 single cards/pairs
  * Bomb/Four of a kind: 4 cards with same rank
  * King bomb: Jokers in a pair



note: the rules have been implemented, but check again to ensure they can correctly verify the inputs. 



## 4\. Workflow Illustration

Device camera continuously catches the picture of user's hand of cards. -> parse the picture to the currently implemented image processor continuously to the to process the image, including image sharpening and adjust the black levels (if not dark then lighten up) -> parse the processed image to the LiteRT inference -> the inference talks to the model to obtain the suit and rank for all cards, pack them into a data structure that can best allow the application to find out the total number of cards remaining in the hands, and available hands -> the application identifies all available hands -> pack the total number and these "hands", and each card's suit and rank to a data structure that can enable the TTS engine to recognize them as fast as possible -> the TTS engine then produce the sound to report the remaining cards, as well as the available hands formed by those cards, to the user. The combos including the most number of cards has to highest priority to be reported to the user, and the combos with the least number of cards has the lower priority to be reported.

This workflow continuously runs when the app is at the home page.

The hands assembly is based on the rules selected by the user in the app settings.

* Current progress: The TTS workflow looks built but actually needs to be redesigned in this workflow:

  * 1\. the tts waits for user's voice input without any interruption from other services.
  * 2\. the user says 'read card' or similar terms to trigger the tts workflow
  * 3\. the camera then starts capturing and feeding the photos to the image processor and so on.
  * 4\. after the main workflow collects all the card info (like number of cards recognized is equal to the maximum number of card of the round, which is a value retrieved from the main workflow's count), the main workflow terminates. 
  * 5\. the tts then replies the number of cards on hand, and available combos.
  * 6\. tts workflow waits again for user's voice input.
  * 7\.  user can say 'read card' again to re-trigger the tts workflow, no matter how many cards on hand. 
* Current progress: The workflow looks okay but need further testing, both unit and integrated.



## 5\. Model Construction

* Create a module to train the model in this project. Use any tech stack you want but make sure to use litrt supported model format, android-ios compatible model format, and the model itself needs to be as lightweight as possible.
* The model should recognize the card's pattern, including rank and suit. This information usually located at the corners of a card. So the model must recognize that pattern despite whatever pattern the card has at the center, to avoid the ambiguity caused by the center pattern (e.g. anything other than suit pattern for numbers, joker protrait for joker cards, and knight, queen, king protrait for J,Q,Ks). The model should be able to recognize the card's rank and suit even if the card is rotated, as long as the corner pattern is visible.
* The model should recognize multiple cards' pattern in the same picture. The result should not be volatile, meaning that the model should be able to recognize the same card's pattern in different pictures with the same result.
* Count the number of cards first, then start recognizing the cards.
* The corner of cards may not display in full, like, users sometimes cannot span out their hands evenly, so the model should be able to recognize the card's pattern even if the corner is partially covered. The model should be able to recognize the card's pattern even if the corner is partially covered by another card, as long as the corner pattern is visible.
* Accuracy of the pattern recognition weights waaaay more than speed.
* Current progress: the model has been built, but the jokers are hard to identify due to the many more variants than the non-joker cards. therefore, i need  to refine the model so that it can recognize as many jokers as possible. The other issue is that the model can only recognize the grey joker -- it recognizes any joker to the grey joker instead of differentiate them. This is also a problem to resolve.

