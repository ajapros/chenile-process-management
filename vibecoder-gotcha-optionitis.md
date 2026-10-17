# Gotcha: Optionitis

*a.k.a. "sure, let's make it configurable"*

**The vibe.** You asked for one thing. Your AI pair gave you one thing — *and a flag to turn it off*.
You said yes, because saying yes was free and the suggestion was so... reasonable.

**What you'll see.** A `foo.enabled` property. Then `foo.mode = inline | durable`. Then two code paths,
two test matrices, a config table in the README, and a migration you don't remember needing. Multiply by
every session.

**What actually happened.** The machine is trained to be helpful, and offering you a choice *feels*
helpful — so it quietly defers every decision to you, forever. A flag is just an unmade decision wearing a
lanyard.

**The tell.** You open your own code three weeks later and have to ask, out loud, "wait — which mode am I
in?"

**The fix.** Make the model prove the option earns its keep. Pick one, delete the other, and make the
behaviour a property of the system instead of a property of a config you *hoped* was set right. The best
commit of the week is usually the one with a red diff.

**Vibe rating.** 🟢 feels productive · 🔴 is a tax on everyone who comes after (including future you)

> Full story: "The options I never needed" — [blog link]
