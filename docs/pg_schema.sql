--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: repos; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE repos (
    repoid character varying(255) NOT NULL,
    taskid character varying(255) NOT NULL,
    using_testing_version boolean DEFAULT true NOT NULL,
    expirydate timestamp with time zone
);


ALTER TABLE public.repos OWNER TO postgres;

--
-- Name: seqsubmission; Type: SEQUENCE; Schema: public; Owner: pottery
--

CREATE SEQUENCE seqsubmission
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.seqsubmission OWNER TO pottery;

--
-- Name: submissions; Type: TABLE; Schema: public; Owner: pottery; Tablespace: 
--

CREATE TABLE submissions (
    submissionid integer NOT NULL,
    repoid character varying(255) NOT NULL,
    tag character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    compilationsuccess boolean,
    compilationresponse text,
    harnesssuccess boolean,
    harnessresponse text,
    validationsuccess boolean,
    validationresponse text,
    compilationfailmessage text,
    harnessfailmessage text,
    validationfailmessage text,
    compilationtimems integer,
    harnesstimems integer,
    validationtimems integer
);


ALTER TABLE public.submissions OWNER TO pottery;

--
-- Name: tasks; Type: TABLE; Schema: public; Owner: pottery; Tablespace: 
--

CREATE TABLE tasks (
    taskid character varying(255) NOT NULL,
    registeredtag character varying(255),
    retired boolean DEFAULT false NOT NULL,
    testingcopyid character varying(255),
    registeredcopyid character varying(255)
);


ALTER TABLE public.tasks OWNER TO pottery;

--
-- Name: repos_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY repos
    ADD CONSTRAINT repos_pkey PRIMARY KEY (repoid);


--
-- Name: submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery; Tablespace: 
--

ALTER TABLE ONLY submissions
    ADD CONSTRAINT submissions_pkey PRIMARY KEY (submissionid);


--
-- Name: tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery; Tablespace: 
--

ALTER TABLE ONLY tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (taskid);


--
-- Name: uniquerepotag; Type: CONSTRAINT; Schema: public; Owner: pottery; Tablespace: 
--

ALTER TABLE ONLY submissions
    ADD CONSTRAINT uniquerepotag UNIQUE (repoid, tag);


--
-- Name: submissions_repoid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY submissions
    ADD CONSTRAINT submissions_repoid_fkey FOREIGN KEY (repoid) REFERENCES repos(repoid) ON DELETE CASCADE;


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- Name: repos; Type: ACL; Schema: public; Owner: postgres
--

REVOKE ALL ON TABLE repos FROM PUBLIC;
REVOKE ALL ON TABLE repos FROM postgres;
GRANT ALL ON TABLE repos TO postgres;
GRANT ALL ON TABLE repos TO pottery;


--
-- PostgreSQL database dump complete
--

