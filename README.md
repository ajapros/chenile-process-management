# Chenile Process Management

Chenile process manager takes care of managing a long process along with sub-processes.
Typical map-reduce processes follow the splitter-aggregator pattern of breaking down a problem
into smaller problems which are solved in their own process. 

We need an overall Process Orchestrator that keeps track of kicking off all the sub-processes 
and ensures that all the sub-processes are completed before the overall process can be marked 
as complete.

Chenile Process Manager addresses this need. 
It is super flexible and can be controlled using a JSON file. 
