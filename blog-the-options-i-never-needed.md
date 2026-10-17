# The options I never needed

Hi folks,

Antoine de Saint-Exupéry had a line I keep coming back to: perfection is reached not when there is
nothing left to add, but when there is nothing left to take away. I thought about it a lot this month,
because I spent a good part of it taking things away from my own code.

Some context. I have been rebuilding the process-management piece of Chenile — the bit that orchestrates
long-running, splitter-aggregator style work — mostly through vibe coding, with Claude and Codex as pair
programmers. It is a genuinely lovely way to work. You describe what you want, the machine writes it, and
the two of you iterate. But I noticed a pattern that I don't think gets talked about enough: vibe coding is
extraordinarily good at giving you options, and almost never tells you that you don't need them.

Here is what I mean. At one point my process manager could run in two modes — a simple inline mode and a
durable, transactional-outbox mode — chosen by a flag. It had a JSON configurator and a database
configurator. It had a separate table to snapshot completion events, and another separate table to record
trigger executions. Every one of these started as a sensible-sounding suggestion during a session. "Shall
we make this configurable?" Yes, of course, why not. When an AI is writing the code, the marginal cost of
adding an option feels like zero.

Except it isn't zero. It is just deferred, and I am the one who pays it.

Each of those options was a fork in the code and a fork in my head. The inline-vs-durable flag meant every
reader had to first ask "which mode am I in?" before they could reason about correctness. It doubled the
paths my tests had to cover. It added a paragraph to the documentation, and then that paragraph had to be
kept true across a dozen more sessions. The two history tables each needed their own migration, their own
retention story, their own box in the ER diagram. None of this was wrong, exactly. It was just needless.

So I started removing. I deleted the inline mode and made the durable outbox the only way the thing runs. I
merged the trigger-execution table into the trigger log. I dropped the completion-snapshot table and derived
completion from the state the process was already in. Each deletion made the code shorter, the tests fewer,
the docs smaller — and, this is the part that surprised me, the guarantees stronger. Once there is only one
execution model, "exactly once" becomes a property of the system rather than of a configuration you hope was
set correctly.

What I have come to believe is that vibe coding quietly moves the hard part of engineering. It used to be
"can I build this?". The machine has largely answered that. The hard part now is "should this be an option
at all?" — and that is a question the AI will almost never ask on your behalf. It is trained to be helpful,
and offering you a choice feels helpful. Saying "no, we will pick one and live with it" is a judgement call,
and judgement is still ours.

There is an irony I enjoy here. The whole point of a flag is to defer a decision. But an unmade decision
doesn't disappear; it simply gets handed to every future reader of the code, forever. A good default, made
once, is a kindness. A toggle left open is a small tax you levy on everyone who comes after — including
yourself three weeks later, blinking at your own options and wondering which one you meant.

I am not against configurability. Some options are load-bearing. A receipt table that guarantees idempotency,
for instance, earns its place, and I argued for keeping it even as I was deleting everything around it. The
skill is in telling the two apart: the option that buys you something real, versus the option you added
because adding it was easy and saying no felt unfriendly.

So here is my new rule when I vibe code. When the machine offers me a choice, I make it prove the choice is
worth keeping. Most of the time it isn't — and the best commit of the week turns out to be the one with a
red diff.

Comments and discussions are welcome. And if you have been vibe coding too, tell me which needless option is
a little too close to home.

(Chenile lives at http://chenile.org, and I write more of these at itmusings.com.)
